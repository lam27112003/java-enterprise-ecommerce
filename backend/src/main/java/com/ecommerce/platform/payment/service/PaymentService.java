package com.ecommerce.platform.payment.service;

import com.ecommerce.platform.order.domain.Order;
import com.ecommerce.platform.order.domain.OrderStatus;
import com.ecommerce.platform.order.exception.OrderExceptions;
import com.ecommerce.platform.order.repository.OrderRepository;
import com.ecommerce.platform.payment.domain.Payment;
import com.ecommerce.platform.payment.domain.PaymentStatus;
import com.ecommerce.platform.payment.dto.PaymentDtos.PayResponse;
import com.ecommerce.platform.payment.dto.PaymentDtos.PaymentResponse;
import com.ecommerce.platform.payment.exception.PaymentExceptions;
import com.ecommerce.platform.payment.provider.CreateIntentCommand;
import com.ecommerce.platform.payment.provider.CreatedIntent;
import com.ecommerce.platform.payment.provider.PaymentProvider;
import com.ecommerce.platform.payment.provider.RetrievedIntent;
import com.ecommerce.platform.payment.repository.PaymentRepository;
import com.ecommerce.platform.security.AuthenticatedUser;
import com.ecommerce.platform.user.domain.RoleCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentCancelService paymentCancelService;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentProvider paymentProvider,
            PaymentCancelService paymentCancelService,
            PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.paymentCancelService = paymentCancelService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public static String payIdempotencyKey(UUID orderId) {
        return "pay:" + orderId;
    }

    public PayResponse pay(UUID userId, UUID orderId) {
        PayPrep prep = prepareOutsideProvider(userId, orderId);
        if (prep.existingProviderRef() != null) {
            RetrievedIntent retrieved = retrieveOutsideTransaction(prep);
            return toPayResponse(prep, retrieved.clientSecret(), prep.existingProviderRef(), prep.status());
        }
        CreatedIntent created = createOutsideTransaction(prep);
        FinalizeResult finalized;
        try {
            finalized = transactionTemplate.execute(status -> finalizeCreate(prep.paymentId(), created));
        } catch (OptimisticLockingFailureException ex) {
            // Concurrent finalize of the same open payment — winner already assigned provider_ref.
            return reloadActivePayResponse(prep.paymentId());
        }
        if (finalized == null) {
            throw PaymentExceptions.providerUnavailable();
        }
        if (finalized.externalCancel()) {
            paymentCancelService.confirmCancel(prep.paymentId());
            throw PaymentExceptions.orderNotPayable();
        }
        return finalized.response();
    }

    private PayResponse reloadActivePayResponse(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(PaymentExceptions::notFound);
        if (payment.getProviderRef() == null || payment.getProviderRef().isBlank()) {
            throw PaymentExceptions.providerUnavailable();
        }
        RetrievedIntent retrieved = retrieveOutsideTransaction(PayPrep.reuse(payment));
        return toPayResponse(PayPrep.reuse(payment), retrieved.clientSecret(), payment.getProviderRef(), payment.getStatus().name());
    }

    @Transactional(readOnly = true)
    public PaymentResponse getForUser(AuthenticatedUser user, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(PaymentExceptions::notFound);
        if (!user.getRoles().contains(RoleCode.ADMIN) && !payment.getUserId().equals(user.getId())) {
            throw PaymentExceptions.notFound();
        }
        return toView(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getForOrder(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(OrderExceptions::notFound);
        if (!order.getUserId().equals(userId)) {
            throw OrderExceptions.notFound();
        }
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(PaymentExceptions::notFound);
        return toView(payment);
    }

    private PayPrep prepareOutsideProvider(UUID userId, UUID orderId) {
        try {
            return transactionTemplate.execute(status -> prepare(userId, orderId));
        } catch (DataIntegrityViolationException ex) {
            return transactionTemplate.execute(status -> prepare(userId, orderId));
        }
    }

    private PayPrep prepare(UUID userId, UUID orderId) {
        Order order = orderRepository.lockById(orderId).orElseThrow(OrderExceptions::notFound);
        if (!order.getUserId().equals(userId)) {
            throw OrderExceptions.notFound();
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw PaymentExceptions.orderNotPayable();
        }
        List<Payment> payments = paymentRepository.lockByOrderId(orderId);
        for (Payment payment : payments) {
            if (payment.getStatus().isActiveAttempt() && payment.getProviderRef() != null) {
                return PayPrep.reuse(payment);
            }
        }
        for (Payment payment : payments) {
            if (payment.getStatus().holdsOpenPaymentSlot()) {
                if (payment.getStatus() == PaymentStatus.CREATE_FAILED) {
                    payment.reopenCreate();
                    paymentRepository.saveAndFlush(payment);
                }
                if (payment.getProviderRef() != null && payment.getStatus().isActiveAttempt()) {
                    return PayPrep.reuse(payment);
                }
                return PayPrep.continueCreate(payment);
            }
        }
        if (!payments.isEmpty()) {
            throw PaymentExceptions.orderNotPayable();
        }
        // Unique idempotency_key (pay:{orderId}) + active_lock_order_id enforce one open attempt.
        // Concurrent losers hit DataIntegrityViolationException; prepareOutsideProvider retries.
        Payment payment = new Payment(
                UUID.randomUUID(),
                order.getId(),
                order.getUserId(),
                order.getTotalCents(),
                order.getCurrency(),
                payIdempotencyKey(order.getId()));
        paymentRepository.saveAndFlush(payment);
        return PayPrep.continueCreate(payment);
    }

    private RetrievedIntent retrieveOutsideTransaction(PayPrep prep) {
        try {
            return paymentProvider.retrieveIntent(prep.existingProviderRef());
        } catch (RuntimeException ex) {
            log.warn("payment retrieve failed paymentId={} orderId={}", prep.paymentId(), prep.orderId());
            throw PaymentExceptions.providerUnavailable();
        }
    }

    private CreatedIntent createOutsideTransaction(PayPrep prep) {
        try {
            return paymentProvider.createPaymentIntent(new CreateIntentCommand(
                    prep.paymentId(),
                    prep.orderId(),
                    prep.amountCents(),
                    prep.currency(),
                    prep.idempotencyKey()));
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status -> markCreateFailed(prep.paymentId()));
            log.warn("payment create failed paymentId={} orderId={}", prep.paymentId(), prep.orderId());
            throw PaymentExceptions.providerUnavailable();
        }
    }

    private void markCreateFailed(UUID paymentId) {
        Payment payment = paymentRepository.lockById(paymentId).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING_CREATE) {
            payment.markCreateFailed();
            paymentRepository.saveAndFlush(payment);
        }
    }

    private FinalizeResult finalizeCreate(UUID paymentId, CreatedIntent created) {
        // Lock order before payment to match prepare()/cancel paths and avoid deadlocks.
        Payment unlocked = paymentRepository.findById(paymentId).orElseThrow(PaymentExceptions::notFound);
        Order order = orderRepository.lockById(unlocked.getOrderId()).orElseThrow(OrderExceptions::notFound);
        Payment payment = paymentRepository.lockById(paymentId).orElseThrow(PaymentExceptions::notFound);
        payment.assignProviderRef(created.providerRef());
        boolean orderClosed = order.getStatus() != OrderStatus.PENDING_PAYMENT;
        if (orderClosed || payment.getStatus() == PaymentStatus.CANCELED
                || payment.getStatus() == PaymentStatus.CANCEL_REQUESTED) {
            payment.markCancelRequested();
            paymentRepository.saveAndFlush(payment);
            return FinalizeResult.cancel();
        }
        if (isPendingProviderStatus(created.providerStatus())) {
            payment.markPending();
        } else {
            payment.markRequiresAction();
        }
        paymentRepository.saveAndFlush(payment);
        log.info(
                "payment intent stored paymentId={} orderId={} providerRef={} status={}",
                payment.getId(),
                payment.getOrderId(),
                payment.getProviderRef(),
                payment.getStatus());
        return FinalizeResult.ready(toPayResponse(
                PayPrep.continueCreate(payment),
                created.clientSecret(),
                payment.getProviderRef(),
                payment.getStatus().name()));
    }

    private static boolean isPendingProviderStatus(String providerStatus) {
        if (providerStatus == null) {
            return false;
        }
        String normalized = providerStatus.toLowerCase(Locale.ROOT);
        return "processing".equals(normalized) || "requires_capture".equals(normalized);
    }

    private static PayResponse toPayResponse(PayPrep prep, String clientSecret, String providerRef, String status) {
        return new PayResponse(
                prep.paymentId(),
                prep.orderId(),
                status,
                prep.amountCents(),
                prep.currency(),
                providerRef,
                clientSecret);
    }

    private static PaymentResponse toView(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getStatus().name(),
                payment.getAmountCents(),
                payment.getCurrency(),
                payment.getProviderRef(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }

    private record PayPrep(
            UUID paymentId,
            UUID orderId,
            long amountCents,
            String currency,
            String idempotencyKey,
            String existingProviderRef,
            String status) {
        private static PayPrep reuse(Payment payment) {
            return new PayPrep(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getAmountCents(),
                    payment.getCurrency(),
                    payment.getIdempotencyKey(),
                    payment.getProviderRef(),
                    payment.getStatus().name());
        }

        private static PayPrep continueCreate(Payment payment) {
            return new PayPrep(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getAmountCents(),
                    payment.getCurrency(),
                    payment.getIdempotencyKey(),
                    null,
                    payment.getStatus().name());
        }
    }

    private record FinalizeResult(PayResponse response, boolean externalCancel) {
        private static FinalizeResult ready(PayResponse response) {
            return new FinalizeResult(response, false);
        }

        private static FinalizeResult cancel() {
            return new FinalizeResult(null, true);
        }
    }
}
