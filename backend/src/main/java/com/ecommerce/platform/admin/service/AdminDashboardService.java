package com.ecommerce.platform.admin.service;

import com.ecommerce.platform.admin.dto.AdminDashboardDtos.DashboardSummaryResponse;
import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.repository.ProductRepository;
import com.ecommerce.platform.config.InventoryProperties;
import com.ecommerce.platform.inventory.repository.InventoryRepository;
import com.ecommerce.platform.order.domain.OrderStatus;
import com.ecommerce.platform.order.repository.OrderRepository;
import com.ecommerce.platform.payment.domain.PaymentStatus;
import com.ecommerce.platform.payment.domain.RefundStatus;
import com.ecommerce.platform.payment.repository.PaymentRepository;
import com.ecommerce.platform.payment.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminDashboardService {

    private static final List<PaymentStatus> REVENUE_PAYMENT_STATUSES = List.of(
            PaymentStatus.SUCCEEDED,
            PaymentStatus.SUCCEEDED_NEEDS_REFUND);

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final InventoryProperties inventoryProperties;

    public AdminDashboardService(
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            InventoryProperties inventoryProperties) {
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.inventoryProperties = inventoryProperties;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Instant from, Instant to) {
        Instant periodFrom = from != null ? from : startOfUtcMonth();
        Instant periodTo = to != null ? to : Instant.now();
        if (periodFrom.isAfter(periodTo)) {
            Instant swap = periodFrom;
            periodFrom = periodTo;
            periodTo = swap;
        }

        Map<String, Long> gross = toCurrencyMap(
                paymentRepository.sumAmountCentsByCurrencyForSucceededAtWindow(
                        REVENUE_PAYMENT_STATUSES,
                        periodFrom,
                        periodTo));
        Map<String, Long> refunded = toCurrencyMapFromRefunds(
                refundRepository.sumAmountCentsByCurrencyForRefundedAtWindow(
                        RefundStatus.REFUNDED,
                        periodFrom,
                        periodTo));
        Map<String, Long> net = mergeNet(gross, refunded);

        return new DashboardSummaryResponse(
                productRepository.count(),
                productRepository.countByStatus(ProductStatus.ACTIVE),
                inventoryRepository.countLowStockActiveProducts(inventoryProperties.getLowStockThreshold()),
                orderRepository.countByStatus(OrderStatus.PENDING_PAYMENT),
                orderRepository.countByStatusIn(EnumSet.of(OrderStatus.PAID, OrderStatus.PROCESSING)),
                refundRepository.countByStatus(RefundStatus.RECONCILIATION_REQUIRED),
                periodFrom,
                periodTo,
                "Payment/refund completion period (UTC)",
                gross,
                refunded,
                net,
                orderRepository.countByCreatedAtBetween(periodFrom, periodTo));
    }

    private static Instant startOfUtcMonth() {
        YearMonth month = YearMonth.now(ZoneOffset.UTC);
        return month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private static Map<String, Long> toCurrencyMap(List<PaymentRepository.CurrencySum> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (PaymentRepository.CurrencySum row : rows) {
            map.put(row.getCurrency(), row.getTotalCents());
        }
        return map;
    }

    private static Map<String, Long> toCurrencyMapFromRefunds(List<RefundRepository.CurrencySum> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (RefundRepository.CurrencySum row : rows) {
            map.put(row.getCurrency(), row.getTotalCents());
        }
        return map;
    }

    private static Map<String, Long> mergeNet(Map<String, Long> gross, Map<String, Long> refunded) {
        Map<String, Long> net = new LinkedHashMap<>();
        gross.keySet().forEach(c -> net.put(c, gross.getOrDefault(c, 0L) - refunded.getOrDefault(c, 0L)));
        for (String currency : refunded.keySet()) {
            net.putIfAbsent(currency, -refunded.getOrDefault(currency, 0L));
        }
        return net;
    }
}
