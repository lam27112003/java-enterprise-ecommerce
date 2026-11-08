package com.ecommerce.platform.payment.service;

import com.ecommerce.platform.order.domain.Order;
import com.ecommerce.platform.order.domain.OrderStatus;
import com.ecommerce.platform.order.exception.OrderExceptions;
import com.ecommerce.platform.order.repository.OrderRepository;
import com.ecommerce.platform.order.service.OrderService;
import com.ecommerce.platform.outbox.domain.OutboxEventTypes;
import com.ecommerce.platform.outbox.domain.ProcessedEvent;
import com.ecommerce.platform.outbox.repository.ProcessedEventRepository;
import com.ecommerce.platform.outbox.service.OutboxDispatcher;
import com.ecommerce.platform.outbox.service.OutboxService;
import com.ecommerce.platform.payment.domain.Payment;
import com.ecommerce.platform.payment.domain.PaymentStatus;
import com.ecommerce.platform.payment.domain.Refund;
import com.ecommerce.platform.payment.domain.RefundReasons;
import com.ecommerce.platform.payment.domain.RefundStatus;
import com.ecommerce.platform.payment.exception.PaymentExceptions;
import com.ecommerce.platform.payment.provider.PaymentProvider;
import com.ecommerce.platform.payment.provider.VerifiedEvent;
import com.ecommerce.platform.payment.repository.PaymentRepository;
import com.ecommerce.platform.payment.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);
    private static final String SOURCE = "stripe";

    private final PaymentProvider paymentProvider;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final RefundBookingService refundBookingService;
    private final OutboxService outboxService;
    private final OutboxDispatcher outboxDispatcher;
    private final ProcessedEventRepository processedEventRepository;
    private final TransactionTemplate transactionTemplate;

    public PaymentWebhookService(
            PaymentProvider paymentProvider,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            OrderRepository orderRepository,
            OrderService orderService,
            RefundBookingService refundBookingService,
            OutboxService outboxService,
            OutboxDispatcher outboxDispatcher,
            ProcessedEventRepository processedEventRepository,
            PlatformTransactionManager transactionManager) {
        this.paymentProvider = paymentProvider;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.refundBookingService = refundBookingService;
        this.outboxService = outboxService;
        this.outboxDispatcher = outboxDispatcher;
        this.processedEventRepository = processedEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void handle(String payload, String signatureHeader) {
        VerifiedEvent event = paymentProvider.constructAndVerifyWebhook(payload, signatureHeader);
        try {
            transactionTemplate.executeWithoutResult(status -> apply(event));
        } catch (DataIntegrityViolationException ex) {
            if (event.eventId() != null
                    && processedEventRepository.existsByConsumerNameAndEventId(SOURCE, event.eventId())) {
                return;
            }
            throw ex;
        }
    }

    private void apply(VerifiedEvent event) {
        if (event.eventId() == null || event.eventId().isBlank() || event.type() == null || event.type().isBlank()) {
            throw PaymentExceptions.invalidWebhook();
        }
        if (processedEventRepository.existsByConsumerNameAndEventId(SOURCE, event.eventId())) {
            return;
        }
        if (event.type().startsWith("payment_intent.")) {
            applyPaymentIntent(event);
        } else if (event.type().startsWith("refund.") || "charge.refunded".equals(event.type())) {
            applyRefundNotification(event);
        } else {
            claim(event.eventId());
        }
        registerDispatchAfterCommit();
    }

    private void applyPaymentIntent(VerifiedEvent event) {
        if (event.objectId() == null || event.objectId().isBlank()) {
            throw PaymentExceptions.invalidWebhook();
        }
        Payment located = paymentRepository.findByProviderRef(event.objectId()).orElse(null);
        if (located == null) {
            claim(event.eventId());
            log.info("stripe webhook ignored unknown providerRef={} eventId={}", event.objectId(), event.eventId());
            return;
        }
        Order order = orderRepository.lockById(located.getOrderId()).orElseThrow(OrderExceptions::notFound);
        order.getItems().size();
        Payment payment = paymentRepository.lockById(located.getId()).orElseThrow(PaymentExceptions::notFound);
        if (requiresAmountCheck(event.type())) {
            assertAmount(order, payment, event);
        }
        claim(event.eventId());
        switch (event.type()) {
            case "payment_intent.succeeded" -> onSucceeded(order, payment);
            case "payment_intent.payment_failed" -> onFailed(order, payment);
            case "payment_intent.canceled" -> onCanceled(order, payment);
            default -> log.info(
                    "stripe webhook stored eventId={} type={} orderId={} paymentId={} providerRef={}",
                    event.eventId(),
                    event.type(),
                    order.getId(),
                    payment.getId(),
                    payment.getProviderRef());
        }
    }

    private void onSucceeded(Order order, Payment payment) {
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            orderService.markPaidFromVerifiedPayment(order.getId());
            payment.markSucceeded();
            paymentRepository.saveAndFlush(payment);
            enqueue(OutboxEventTypes.ORDER_PAID, "order", order.getId().toString(), order.getId(), payment);
            enqueue(OutboxEventTypes.PAYMENT_SUCCEEDED, "payment", payment.getId().toString(), order.getId(), payment);
            log.info(
                    "payment succeeded orderId={} paymentId={} providerRef={}",
                    order.getId(),
                    payment.getId(),
                    payment.getProviderRef());
            return;
        }
        if (order.getStatus() == OrderStatus.PAID) {
            if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                payment.markSucceeded();
                paymentRepository.saveAndFlush(payment);
            }
            log.info(
                    "payment success idempotent orderId={} paymentId={} providerRef={}",
                    order.getId(),
                    payment.getId(),
                    payment.getProviderRef());
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            refundBookingService.bookLateSuccess(payment, order.getId());
            log.info(
                    "late payment success refund booked orderId={} paymentId={} providerRef={} orderStatus={}",
                    order.getId(),
                    payment.getId(),
                    payment.getProviderRef(),
                    order.getStatus());
        }
    }

    private void onFailed(Order order, Payment payment) {
        boolean moved = order.getStatus() == OrderStatus.PENDING_PAYMENT;
        if (moved) {
            orderService.markUnpaidTerminalFromPayment(order.getId(), OrderStatus.PAYMENT_FAILED);
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED
                && payment.getStatus() != PaymentStatus.SUCCEEDED_NEEDS_REFUND) {
            payment.markFailed();
            paymentRepository.saveAndFlush(payment);
            if (moved) {
                enqueue(OutboxEventTypes.PAYMENT_FAILED, "payment", payment.getId().toString(), order.getId(), payment);
            }
        }
        log.info(
                "payment failed orderId={} paymentId={} providerRef={}",
                order.getId(),
                payment.getId(),
                payment.getProviderRef());
    }

    private void onCanceled(Order order, Payment payment) {
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            orderService.markUnpaidTerminalFromPayment(order.getId(), OrderStatus.PAYMENT_FAILED);
            enqueue(OutboxEventTypes.PAYMENT_FAILED, "payment", payment.getId().toString(), order.getId(), payment);
        }
        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.SUCCEEDED_NEEDS_REFUND) {
            return;
        }
        payment.markCanceled();
        paymentRepository.saveAndFlush(payment);
        log.info(
                "payment canceled orderId={} paymentId={} providerRef={}",
                order.getId(),
                payment.getId(),
                payment.getProviderRef());
    }

    private void applyRefundNotification(VerifiedEvent event) {
        claim(event.eventId());
        Refund refund = locateRefund(event);
        if (refund == null) {
            return;
        }
        refund = refundRepository.lockById(refund.getId()).orElse(refund);
        if ("succeeded".equalsIgnoreCase(event.status())) {
            if (refund.getStatus() != RefundStatus.REFUNDED) {
                refund.markRefunded(event.objectId());
                refundRepository.saveAndFlush(refund);
            }
            log.info(
                    "refund confirmed from webhook orderId={} paymentId={} refundId={} reasonCode={} stripeRefundId={}",
                    refund.getOrderId(),
                    refund.getPaymentId(),
                    refund.getId(),
                    refund.getReasonCode(),
                    refund.getProviderRefundRef());
            return;
        }
        if ("failed".equalsIgnoreCase(event.status()) || "canceled".equalsIgnoreCase(event.status())) {
            if (refund.getStatus() != RefundStatus.REFUNDED) {
                refund.markReconciliationRequired(event.objectId());
                refundRepository.saveAndFlush(refund);
            }
            log.error(
                    "refund reconciliation required orderId={} paymentId={} refundId={} reasonCode={} stripeRefundId={}",
                    refund.getOrderId(),
                    refund.getPaymentId(),
                    refund.getId(),
                    refund.getReasonCode(),
                    event.objectId());
        }
    }

    private Refund locateRefund(VerifiedEvent event) {
        if (event.objectId() != null) {
            Refund byRef = refundRepository.findByProviderRefundRef(event.objectId()).orElse(null);
            if (byRef != null) {
                return byRef;
            }
        }
        String providerRef = event.paymentIntentId() != null ? event.paymentIntentId() : event.objectId();
        if (providerRef == null) {
            return null;
        }
        Payment payment = paymentRepository.findByProviderRef(providerRef).orElse(null);
        if (payment == null) {
            return null;
        }
        return refundRepository.findByPaymentIdAndReasonCode(payment.getId(), RefundReasons.LATE_SUCCESS).orElse(null);
    }

    private void assertAmount(Order order, Payment payment, VerifiedEvent event) {
        if (event.amountCents() == null || event.currency() == null || event.currency().isBlank()) {
            throw PaymentExceptions.amountMismatch();
        }
        if (event.amountCents() != order.getTotalCents() || event.amountCents() != payment.getAmountCents()) {
            throw PaymentExceptions.amountMismatch();
        }
        if (!event.currency().equalsIgnoreCase(order.getCurrency())
                || !event.currency().equalsIgnoreCase(payment.getCurrency())) {
            throw PaymentExceptions.amountMismatch();
        }
    }

    private static boolean requiresAmountCheck(String type) {
        return "payment_intent.succeeded".equals(type)
                || "payment_intent.payment_failed".equals(type)
                || "payment_intent.canceled".equals(type);
    }

    private void claim(String eventId) {
        processedEventRepository.saveAndFlush(new ProcessedEvent(UUID.randomUUID(), SOURCE, eventId));
    }

    private void enqueue(String eventType, String aggregateType, String aggregateId, UUID orderId, Payment payment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("paymentId", payment.getId().toString());
        payload.put("providerRef", payment.getProviderRef());
        payload.put("amountCents", payment.getAmountCents());
        payload.put("currency", payment.getCurrency());
        outboxService.save(aggregateType, aggregateId, eventType, payload);
    }

    private void registerDispatchAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            outboxDispatcher.dispatchPending();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    outboxDispatcher.dispatchPending();
                } catch (RuntimeException ex) {
                    log.error("outbox dispatch failed after webhook commit");
                }
            }
        });
    }
}
