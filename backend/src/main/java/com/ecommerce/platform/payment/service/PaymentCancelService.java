package com.ecommerce.platform.payment.service;

import com.ecommerce.platform.order.domain.Order;
import com.ecommerce.platform.order.domain.OrderStatus;
import com.ecommerce.platform.order.repository.OrderRepository;
import com.ecommerce.platform.order.spi.UnpaidTerminalPaymentHook;
import com.ecommerce.platform.outbox.service.OutboxDispatcher;
import com.ecommerce.platform.payment.domain.Payment;
import com.ecommerce.platform.payment.domain.PaymentStatus;
import com.ecommerce.platform.payment.provider.CancelKind;
import com.ecommerce.platform.payment.provider.CancelOutcome;
import com.ecommerce.platform.payment.provider.PaymentProvider;
import com.ecommerce.platform.payment.provider.PaymentProviderException;
import com.ecommerce.platform.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Attempts to cancel an active PaymentIntent only after the order transaction commits.
 * Local status stays {@code CANCEL_REQUESTED} until Stripe confirms cancel (P6-D8).
 */
@Service
public class PaymentCancelService implements UnpaidTerminalPaymentHook {

    private static final Logger log = LoggerFactory.getLogger(PaymentCancelService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProvider paymentProvider;
    private final RefundBookingService refundBookingService;
    private final OutboxDispatcher outboxDispatcher;
    private final TransactionTemplate requiresNew;

    public PaymentCancelService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentProvider paymentProvider,
            RefundBookingService refundBookingService,
            OutboxDispatcher outboxDispatcher,
            PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentProvider = paymentProvider;
        this.refundBookingService = refundBookingService;
        this.outboxDispatcher = outboxDispatcher;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void onUnpaidTerminal(UUID orderId) {
        try {
            List<UUID> paymentIds = requiresNew.execute(status -> paymentRepository.lockByOrderId(orderId).stream()
                    .filter(payment -> payment.getStatus().isActiveAttempt()
                            || payment.getStatus() == PaymentStatus.PENDING_CREATE)
                    .map(Payment::getId)
                    .toList());
            if (paymentIds == null) {
                return;
            }
            for (UUID paymentId : paymentIds) {
                attemptOne(orderId, paymentId);
            }
        } catch (RuntimeException ex) {
            log.error("payment cancel attempt failed orderId={}", orderId);
        }
    }

    public void confirmCancel(UUID paymentId) {
        String providerRef = paymentRepository.findById(paymentId)
                .map(Payment::getProviderRef)
                .orElse(null);
        if (providerRef == null || providerRef.isBlank()) {
            return;
        }
        callProviderAndApply(paymentId, providerRef);
    }

    private void attemptOne(UUID orderId, UUID paymentId) {
        String providerRef = requiresNew.execute(status -> prepareCancel(orderId, paymentId));
        if (providerRef == null || providerRef.isBlank()) {
            return;
        }
        callProviderAndApply(paymentId, providerRef);
    }

    private String prepareCancel(UUID orderId, UUID paymentId) {
        Payment payment = paymentRepository.lockById(paymentId).orElse(null);
        if (payment == null) {
            return null;
        }
        PaymentStatus status = payment.getStatus();
        if (status == PaymentStatus.PENDING_CREATE && payment.getProviderRef() == null) {
            payment.markCanceled();
            paymentRepository.saveAndFlush(payment);
            log.info("payment create abandoned orderId={} paymentId={}", orderId, paymentId);
            return null;
        }
        if ((status == PaymentStatus.REQUIRES_ACTION || status == PaymentStatus.PENDING)
                && payment.getProviderRef() != null) {
            payment.markCancelRequested();
            paymentRepository.saveAndFlush(payment);
            return payment.getProviderRef();
        }
        if (status == PaymentStatus.CANCEL_REQUESTED && payment.getProviderRef() != null) {
            return payment.getProviderRef();
        }
        return null;
    }

    private void callProviderAndApply(UUID paymentId, String providerRef) {
        CancelOutcome outcome;
        try {
            outcome = paymentProvider.cancelPaymentIntent(providerRef);
        } catch (PaymentProviderException ex) {
            log.warn(
                    "payment cancel not confirmed paymentId={} providerRef={} outcome=uncertain",
                    paymentId,
                    providerRef);
            return;
        } catch (RuntimeException ex) {
            log.warn(
                    "payment cancel not confirmed paymentId={} providerRef={} outcome=uncertain",
                    paymentId,
                    providerRef);
            return;
        }
        boolean refundBooked = Boolean.TRUE.equals(requiresNew.execute(status -> applyOutcome(paymentId, outcome)));
        if (refundBooked) {
            outboxDispatcher.dispatchPending();
        }
    }

    private boolean applyOutcome(UUID paymentId, CancelOutcome outcome) {
        Payment payment = paymentRepository.lockById(paymentId).orElse(null);
        if (payment == null) {
            return false;
        }
        if (outcome.kind() == CancelKind.CANCELED) {
            if (payment.getStatus() == PaymentStatus.CANCEL_REQUESTED
                    || payment.getStatus() == PaymentStatus.REQUIRES_ACTION
                    || payment.getStatus() == PaymentStatus.PENDING) {
                payment.markCanceled();
                paymentRepository.saveAndFlush(payment);
                log.info(
                        "payment cancel confirmed paymentId={} providerRef={} orderId={}",
                        payment.getId(),
                        payment.getProviderRef(),
                        payment.getOrderId());
            }
            return false;
        }
        if (outcome.kind() == CancelKind.SUCCEEDED_INSTEAD) {
            Order order = orderRepository.lockById(payment.getOrderId()).orElse(null);
            if (order == null) {
                return false;
            }
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.PAYMENT_FAILED) {
                refundBookingService.bookLateSuccess(payment, order.getId());
                return true;
            }
            log.warn(
                    "payment cancel saw success while order is {} orderId={} paymentId={} providerRef={}",
                    order.getStatus(),
                    order.getId(),
                    payment.getId(),
                    payment.getProviderRef());
            return false;
        }
        log.warn(
                "payment cancel not confirmed paymentId={} providerRef={} providerStatus={}",
                payment.getId(),
                payment.getProviderRef(),
                outcome.providerStatus());
        return false;
    }
}
