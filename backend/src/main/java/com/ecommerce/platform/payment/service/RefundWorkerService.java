package com.ecommerce.platform.payment.service;

import com.ecommerce.platform.payment.domain.Payment;
import com.ecommerce.platform.payment.domain.Refund;
import com.ecommerce.platform.payment.domain.RefundStatus;
import com.ecommerce.platform.payment.provider.CreateRefundCommand;
import com.ecommerce.platform.payment.provider.PaymentProvider;
import com.ecommerce.platform.payment.provider.PaymentProviderException;
import com.ecommerce.platform.payment.provider.RefundOutcome;
import com.ecommerce.platform.payment.provider.RefundOutcomeKind;
import com.ecommerce.platform.payment.repository.PaymentRepository;
import com.ecommerce.platform.payment.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Submits a refund only after {@code REFUND_SUBMITTED} is committed, then records the
 * confirmed provider outcome. Uncertain results become {@code RECONCILIATION_REQUIRED}
 * and are not retried blindly (P6-D7 / P6-D10).
 */
@Service
public class RefundWorkerService {

    private static final Logger log = LoggerFactory.getLogger(RefundWorkerService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final TransactionTemplate requiresNew;

    public RefundWorkerService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            PaymentProvider paymentProvider,
            PlatformTransactionManager transactionManager) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.paymentProvider = paymentProvider;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void submit(UUID refundId) {
        Claimed claimed = requiresNew.execute(status -> claim(refundId));
        if (claimed == null) {
            return;
        }
        RefundOutcome outcome;
        try {
            outcome = paymentProvider.createRefund(new CreateRefundCommand(
                    claimed.providerRef(),
                    claimed.amountCents(),
                    claimed.idempotencyKey()));
        } catch (PaymentProviderException ex) {
            markReconciliation(refundId, null, ex.isUncertain() ? "uncertain" : "failed");
            return;
        } catch (RuntimeException ex) {
            markReconciliation(refundId, null, "uncertain");
            return;
        }
        requiresNew.executeWithoutResult(status -> applyOutcome(refundId, outcome));
    }

    private Claimed claim(UUID refundId) {
        Refund refund = refundRepository.lockById(refundId).orElse(null);
        if (refund == null || refund.getStatus() != RefundStatus.REFUND_PENDING) {
            return null;
        }
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElse(null);
        if (payment == null || payment.getProviderRef() == null) {
            refund.markReconciliationRequired(null);
            refundRepository.saveAndFlush(refund);
            log.error(
                    "refund reconciliation required orderId={} paymentId={} refundId={} reasonCode={} providerRef=null",
                    refund.getOrderId(),
                    refund.getPaymentId(),
                    refund.getId(),
                    refund.getReasonCode());
            return null;
        }
        refund.markSubmitted();
        refundRepository.saveAndFlush(refund);
        return new Claimed(
                payment.getProviderRef(),
                refund.getAmountCents(),
                refund.getIdempotencyKey(),
                refund.getOrderId(),
                payment.getId(),
                refund.getReasonCode());
    }

    private void applyOutcome(UUID refundId, RefundOutcome outcome) {
        Refund refund = refundRepository.lockById(refundId).orElse(null);
        if (refund == null || refund.getStatus() == RefundStatus.REFUNDED) {
            return;
        }
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElse(null);
        String providerRef = payment == null ? null : payment.getProviderRef();
        if (outcome.kind() == RefundOutcomeKind.CONFIRMED_SUCCESS) {
            refund.markRefunded(outcome.providerRefundRef());
            refundRepository.saveAndFlush(refund);
            log.info(
                    "refund confirmed orderId={} paymentId={} providerRef={} refundId={} reasonCode={} stripeRefundId={}",
                    refund.getOrderId(),
                    refund.getPaymentId(),
                    providerRef,
                    refund.getId(),
                    refund.getReasonCode(),
                    outcome.providerRefundRef());
            return;
        }
        refund.markReconciliationRequired(outcome.providerRefundRef());
        refundRepository.saveAndFlush(refund);
        log.error(
                "refund reconciliation required orderId={} paymentId={} providerRef={} refundId={} reasonCode={} stripeRefundId={} providerStatus={}",
                refund.getOrderId(),
                refund.getPaymentId(),
                providerRef,
                refund.getId(),
                refund.getReasonCode(),
                outcome.providerRefundRef(),
                outcome.providerStatus());
    }

    private void markReconciliation(UUID refundId, String providerRefundRef, String outcome) {
        requiresNew.executeWithoutResult(status -> {
            Refund refund = refundRepository.lockById(refundId).orElse(null);
            if (refund == null || refund.getStatus() == RefundStatus.REFUNDED) {
                return;
            }
            refund.markReconciliationRequired(providerRefundRef);
            refundRepository.saveAndFlush(refund);
            log.error(
                    "refund reconciliation required orderId={} paymentId={} refundId={} reasonCode={} outcome={}",
                    refund.getOrderId(),
                    refund.getPaymentId(),
                    refund.getId(),
                    refund.getReasonCode(),
                    outcome);
        });
    }

    private record Claimed(
            String providerRef,
            long amountCents,
            String idempotencyKey,
            UUID orderId,
            UUID paymentId,
            String reasonCode) {
    }
}
