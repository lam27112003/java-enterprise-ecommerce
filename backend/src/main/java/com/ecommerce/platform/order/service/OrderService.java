package com.ecommerce.platform.order.service;

import com.ecommerce.platform.cart.domain.Cart;
import com.ecommerce.platform.cart.domain.CartItem;
import com.ecommerce.platform.cart.repository.CartRepository;
import com.ecommerce.platform.catalog.domain.Product;
import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.repository.ProductRepository;
import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.common.exception.BusinessException;
import com.ecommerce.platform.inventory.exception.InventoryExceptions;
import com.ecommerce.platform.inventory.service.InventoryService;
import com.ecommerce.platform.order.domain.CheckoutIdempotencyKey;
import com.ecommerce.platform.order.domain.Order;
import com.ecommerce.platform.order.domain.OrderItem;
import com.ecommerce.platform.order.domain.OrderStatus;
import com.ecommerce.platform.order.domain.OrderStatusHistory;
import com.ecommerce.platform.order.domain.OrderStatusTransitions;
import com.ecommerce.platform.order.dto.OrderDtos.AdminStatusUpdateRequest;
import com.ecommerce.platform.order.dto.OrderDtos.CheckoutRequest;
import com.ecommerce.platform.order.dto.OrderDtos.OrderItemResponse;
import com.ecommerce.platform.order.dto.OrderDtos.OrderResponse;
import com.ecommerce.platform.order.dto.OrderDtos.OrderStatusHistoryResponse;
import com.ecommerce.platform.order.exception.OrderExceptions;
import com.ecommerce.platform.order.repository.CheckoutIdempotencyKeyRepository;
import com.ecommerce.platform.order.repository.OrderRepository;
import com.ecommerce.platform.order.repository.OrderStatusHistoryRepository;
import com.ecommerce.platform.order.spi.UnpaidTerminalPaymentHook;
import com.ecommerce.platform.promotion.domain.Promotion;
import com.ecommerce.platform.promotion.service.PromotionPricing;
import com.ecommerce.platform.promotion.service.PromotionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_CHECKOUT_RETRIES = 8;

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CheckoutIdempotencyKeyRepository idempotencyKeyRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final PromotionService promotionService;
    private final ObjectProvider<UnpaidTerminalPaymentHook> unpaidTerminalPaymentHooks;
    private final TransactionTemplate transactionTemplate;

    public OrderService(
            OrderRepository orderRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            CheckoutIdempotencyKeyRepository idempotencyKeyRepository,
            CartRepository cartRepository,
            ProductRepository productRepository,
            InventoryService inventoryService,
            PromotionService promotionService,
            ObjectProvider<UnpaidTerminalPaymentHook> unpaidTerminalPaymentHooks,
            PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.promotionService = promotionService;
        this.unpaidTerminalPaymentHooks = unpaidTerminalPaymentHooks;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public OrderResponse checkout(UUID userId, String idempotencyKey, CheckoutRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw OrderExceptions.idempotencyKeyRequired();
        }
        String key = idempotencyKey.trim();
        if (key.length() > 128) {
            throw OrderExceptions.idempotencyKeyRequired();
        }
        CheckoutRequest safeRequest = request == null ? new CheckoutRequest(null, null) : request;
        String requestHash = CheckoutIdempotencyHasher.hash(safeRequest);

        var existing = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, key);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), requestHash);
        }

        OptimisticLockingFailureException lastOlfe = null;
        for (int attempt = 0; attempt < MAX_CHECKOUT_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status ->
                        doCheckoutInTransaction(userId, key, requestHash, safeRequest));
            } catch (BusinessException ex) {
                var raced = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, key);
                if (raced.isPresent()) {
                    return replayOrConflict(raced.get(), requestHash);
                }
                throw ex;
            } catch (DataIntegrityViolationException ex) {
                var raced = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, key);
                if (raced.isPresent()) {
                    return replayOrConflict(raced.get(), requestHash);
                }
                throw ex;
            } catch (OptimisticLockingFailureException ex) {
                lastOlfe = ex;
                var raced = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, key);
                if (raced.isPresent()) {
                    return replayOrConflict(raced.get(), requestHash);
                }
                try {
                    Thread.sleep(5L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw InventoryExceptions.concurrentModification();
                }
            }
        }
        throw lastOlfe != null ? lastOlfe : InventoryExceptions.concurrentModification();
    }

    private OrderResponse replayOrConflict(CheckoutIdempotencyKey record, String requestHash) {
        if (!record.getRequestHash().equals(requestHash)) {
            throw OrderExceptions.idempotencyConflict();
        }
        return toResponse(requireOrderWithItems(record.getOrderId()));
    }

    private OrderResponse doCheckoutInTransaction(
            UUID userId,
            String idempotencyKey,
            String requestHash,
            CheckoutRequest request) {
        var existing = idempotencyKeyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), requestHash);
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(OrderExceptions::emptyCart);
        if (cart.getItems().isEmpty()) {
            throw OrderExceptions.emptyCart();
        }

        List<CartItem> lines = new ArrayList<>(cart.getItems());
        lines.sort(Comparator.comparing(CartItem::getProductId));

        List<ResolvedLine> resolved = new ArrayList<>();
        String currency = null;
        long subtotal = 0L;
        for (CartItem cartItem : lines) {
            Product product = productRepository.findById(cartItem.getProductId())
                    .orElseThrow(OrderExceptions::productUnavailable);
            if (product.getDeletedAt() != null || product.getStatus() != ProductStatus.ACTIVE) {
                throw OrderExceptions.productUnavailable();
            }
            if (currency == null) {
                currency = product.getCurrency();
            } else if (!currency.equalsIgnoreCase(product.getCurrency())) {
                throw OrderExceptions.currencyMismatch();
            }
            long unitPrice = product.getPriceCents();
            long lineTotal = Math.multiplyExact(unitPrice, cartItem.getQuantity());
            subtotal = Math.addExact(subtotal, lineTotal);
            resolved.add(new ResolvedLine(product, cartItem.getQuantity(), unitPrice, lineTotal));
        }

        long discount = 0L;
        Promotion promotion = null;
        if (request.promotionCode() != null && !request.promotionCode().isBlank()) {
            promotion = promotionService.requireApplicableForUpdate(request.promotionCode(), subtotal);
            discount = PromotionPricing.discountCents(promotion, subtotal);
        }
        long tax = 0L;
        long total = PromotionPricing.totalCents(subtotal, discount, tax);

        for (ResolvedLine line : resolved) {
            inventoryService.ensureInventoryExists(line.product().getId());
            if (inventoryService.getAvailableQuantity(line.product().getId()) < line.quantity()) {
                throw OrderExceptions.insufficientStock();
            }
        }
        for (ResolvedLine line : resolved) {
            inventoryService.reserve(line.product().getId(), line.quantity());
        }

        Order order = new Order(
                UUID.randomUUID(),
                generateOrderNumber(),
                userId,
                OrderStatus.PENDING_PAYMENT,
                subtotal,
                discount,
                tax,
                total,
                currency == null ? "USD" : currency.toUpperCase(Locale.ROOT)
        );
        if (request.shippingAddressJson() != null && !request.shippingAddressJson().isBlank()) {
            order.setShippingAddressJson(request.shippingAddressJson().trim());
        }
        if (promotion != null) {
            order.setPromotionReference(promotion.getId(), promotion.getCode());
        }
        for (ResolvedLine line : resolved) {
            order.addItem(new OrderItem(
                    UUID.randomUUID(),
                    order,
                    line.product().getId(),
                    line.product().getName(),
                    line.product().getSku(),
                    line.unitPriceCents(),
                    line.quantity()
            ));
        }
        orderRepository.saveAndFlush(order);

        if (promotion != null) {
            promotionService.redeem(promotion, userId, order.getId());
        }

        idempotencyKeyRepository.saveAndFlush(new CheckoutIdempotencyKey(
                UUID.randomUUID(),
                userId,
                idempotencyKey,
                requestHash,
                order.getId()
        ));

        cart.clearItems();
        cartRepository.saveAndFlush(cart);

        return toResponse(requireOrderWithItems(order.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listMine(UUID userId, int page, int size) {
        Page<Order> result = orderRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.from(result.map(o -> toResponse(requireOrderWithItems(o.getId()))));
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(UUID userId, UUID orderId) {
        return toResponse(orderRepository.findWithItemsByIdAndUserId(orderId, userId)
                .orElseThrow(OrderExceptions::notFound));
    }

    @Transactional(readOnly = true)
    public OrderResponse getForAdmin(UUID orderId) {
        return toResponse(requireOrderWithItems(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listAdmin(OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return PageResponse.from(result.map(o -> toResponse(requireOrderWithItems(o.getId()))));
    }

    @Transactional
    public OrderResponse cancelMine(UUID userId, UUID orderId) {
        Order order = orderRepository.findWithItemsByIdAndUserId(orderId, userId)
                .orElseThrow(OrderExceptions::notFound);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toResponse(order);
        }
        if (!OrderStatusTransitions.isAllowedCustomerCancel(order.getStatus())) {
            throw OrderExceptions.cancelNotAllowed();
        }
        OrderStatus previous = order.getStatus();
        compensateUnpaidTerminal(order, OrderStatus.CANCELLED);
        recordStatusHistory(order.getId(), previous, OrderStatus.CANCELLED, userId, null);
        return toResponse(requireOrderWithItems(order.getId()));
    }

    @Transactional
    public OrderResponse adminUpdateStatus(UUID orderId, UUID actorUserId, AdminStatusUpdateRequest request) {
        if (request == null || request.status() == null) {
            throw OrderExceptions.illegalStatusTransition();
        }
        Order order = requireOrderWithItems(orderId);
        if (order.getStatus() == request.status()) {
            return toResponse(order);
        }
        OrderStatus previous = order.getStatus();
        OrderStatus target = request.status();
        if (OrderStatusTransitions.isUnpaidAdminTransition(previous, target)) {
            compensateUnpaidTerminal(order, target);
            recordStatusHistory(order.getId(), previous, target, actorUserId, request.note());
        } else if (OrderStatusTransitions.isFulfillmentAdminTransition(previous, target)) {
            try {
                order.applyAdminFulfillmentStatus(target, request.trackingNumber(), request.carrier());
            } catch (IllegalStateException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("trackingNumber")) {
                    throw OrderExceptions.shippingFieldsRequired();
                }
                throw OrderExceptions.illegalStatusTransition();
            }
            orderRepository.saveAndFlush(order);
            recordStatusHistory(order.getId(), previous, target, actorUserId, request.note());
        } else {
            throw OrderExceptions.illegalStatusTransition();
        }
        return toResponse(requireOrderWithItems(order.getId()));
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> statusHistoryForAdmin(UUID orderId) {
        requireOrderWithItems(orderId);
        return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(h -> new OrderStatusHistoryResponse(
                        h.getId(),
                        h.getPreviousStatus(),
                        h.getNewStatus(),
                        h.getActorUserId(),
                        h.getNote(),
                        h.getCreatedAt()))
                .toList();
    }

    private void compensateUnpaidTerminal(Order order, OrderStatus target) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            // already terminal — idempotent no-op for duplicate cancel/status
            if (order.getStatus() == target) {
                return;
            }
            throw OrderExceptions.illegalStatusTransition();
        }
        order.applyAdminStatus(target);

        if (order.markInventoryReleasedOnce()) {
            for (OrderItem item : order.getItems()) {
                inventoryService.release(item.getProductId(), item.getQuantity());
            }
        }
        if (order.getPromotionId() != null && order.markPromotionUsageRestoredOnce()) {
            promotionService.restoreUsageForOrder(order.getId());
        }
        orderRepository.saveAndFlush(order);
        registerUnpaidTerminalHook(order.getId());
    }

    /**
     * Payment-module entry point. Joins the caller transaction. Only a verified provider
     * success may call this, and only while the order is still {@code PENDING_PAYMENT}.
     */
    @Transactional
    public void markPaidFromVerifiedPayment(UUID orderId) {
        Order order = orderRepository.lockById(orderId).orElseThrow(OrderExceptions::notFound);
        order.getItems().size();
        OrderStatus previous = order.getStatus();
        order.markPaidFromPayment();
        if (order.markInventoryCommittedOnce()) {
            for (OrderItem item : order.getItems()) {
                inventoryService.commit(item.getProductId(), item.getQuantity());
            }
        }
        orderRepository.saveAndFlush(order);
        if (previous != OrderStatus.PAID) {
            recordStatusHistory(orderId, previous, OrderStatus.PAID, null, null);
        }
    }

    /**
     * Payment failure or provider cancellation while the order is still unpaid and open.
     * Terminal orders are left unchanged. Compensation runs at most once.
     */
    @Transactional
    public void markUnpaidTerminalFromPayment(UUID orderId, OrderStatus target) {
        if (target != OrderStatus.CANCELLED && target != OrderStatus.PAYMENT_FAILED) {
            throw OrderExceptions.illegalStatusTransition();
        }
        Order order = orderRepository.lockById(orderId).orElseThrow(OrderExceptions::notFound);
        order.getItems().size();
        if (order.getStatus() == target || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }
        OrderStatus previous = order.getStatus();
        compensateUnpaidTerminal(order, target);
        recordStatusHistory(orderId, previous, target, null, null);
    }

    private void recordStatusHistory(
            UUID orderId,
            OrderStatus previous,
            OrderStatus next,
            UUID actorUserId,
            String note) {
        if (previous == next) {
            return;
        }
        orderStatusHistoryRepository.save(new OrderStatusHistory(
                UUID.randomUUID(),
                orderId,
                previous,
                next,
                actorUserId,
                note));
    }

    private void registerUnpaidTerminalHook(UUID orderId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invokeUnpaidTerminalHook(orderId);
                }
            });
            return;
        }
        invokeUnpaidTerminalHook(orderId);
    }

    private void invokeUnpaidTerminalHook(UUID orderId) {
        try {
            UnpaidTerminalPaymentHook hook = unpaidTerminalPaymentHooks.getIfAvailable();
            if (hook != null) {
                hook.onUnpaidTerminal(orderId);
            }
        } catch (RuntimeException ex) {
            log.error("unpaid terminal payment hook failed orderId={}", orderId);
        }
    }

    private Order requireOrderWithItems(UUID orderId) {
        return orderRepository.findWithItemsById(orderId).orElseThrow(OrderExceptions::notFound);
    }

    private static String generateOrderNumber() {
        return "ORD-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT)
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getId(),
                        i.getProductId(),
                        i.getProductName(),
                        i.getSku(),
                        i.getUnitPriceCents(),
                        i.getQuantity(),
                        i.getLineTotalCents()
                ))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getStatus(),
                order.getSubtotalCents(),
                order.getDiscountCents(),
                order.getTaxCents(),
                order.getTotalCents(),
                order.getCurrency(),
                order.getShippingAddressJson(),
                order.getTrackingNumber(),
                order.getCarrier(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getPaidAt(),
                order.getPromotionId(),
                order.getPromotionCode(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private record ResolvedLine(Product product, int quantity, long unitPriceCents, long lineTotalCents) {
    }
}
