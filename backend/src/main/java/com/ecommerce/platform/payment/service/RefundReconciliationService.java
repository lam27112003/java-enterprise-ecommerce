package com.ecommerce.platform.payment.service;

import com.ecommerce.platform.common.exception.BusinessException;
import com.ecommerce.platform.config.StripeProperties;
import com.ecommerce.platform.payment.domain.Payment;
import com.ecommerce.platform.payment.domain.Refund;
import com.ecommerce.platform.payment.domain.RefundReconciliationAudit;
import com.ecommerce.platform.payment.domain.RefundReconciliationAuditAction;
import com.ecommerce.platform.payment.domain.RefundStatus;
import com.ecommerce.platform.payment.exception.PaymentExceptions;
import com.ecommerce.platform.payment.provider.PaymentProvider;
import com.ecommerce.platform.payment.provider.PaymentProviderException;
import com.ecommerce.platform.payment.provider.RetrievedRefund;
import com.ecommerce.platform.payment.repository.PaymentRepository;
import com.ecommerce.platform.payment.repository.RefundReconciliationAuditRepository;
import com.ecommerce.platform.payment.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Provider-verified refund reconciliation (P6-D7 / P7-D10). Stripe I/O runs outside DB transactions.
 */
@Service
public class RefundReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(RefundReconciliationService.class);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundReconciliationAuditRepository auditRepository;
    private final RefundWorkerService refundWorkerService;
    private final PaymentProvider paymentProvider;
    private final StripeProperties stripeProperties;
    private final TransactionTemplate transactionTemplate;

    public RefundReconciliationService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            RefundReconciliationAuditRepository auditRepository,
            RefundWorkerService refundWorkerService,
            PaymentProvider paymentProvider,
            StripeProperties stripeProperties,
            PlatformTransactionManager transactionManager) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.auditRepository = auditRepository;
        this.refundWorkerService = refundWorkerService;
        this.paymentProvider = paymentProvider;
        this.stripeProperties = stripeProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * When Stripe already shows a successful refund for this payment, verify via provider then record locally.
     */
    public void confirmExternalRefund(UUID refundId, UUID actorUserId, String providerRefundRef, String note) {
        requireActor(actorUserId);
        ConfirmSnapshot snapshot = transactionTemplate.execute(status -> loadConfirmSnapshot(refundId));
        if (snapshot == null) {
            throw PaymentExceptions.refundNotFound();
        }

        RetrievedRefund retrieved;
        try {
            retrieved = paymentProvider.retrieveRefund(providerRefundRef);
        } catch (PaymentProviderException ex) {
            if (ex.isUncertain()) {
                appendAudit(
                        refundId,
                        actorUserId,
                        RefundReconciliationAuditAction.CONFIRM_EXTERNAL_UNCERTAIN,
                        snapshot.previousStatus(),
                        snapshot.previousStatus(),
                        providerRefundRef,
                        note);
                throw PaymentExceptions.refundConfirmUncertain();
            }
            appendAudit(
                    refundId,
                    actorUserId,
                    RefundReconciliationAuditAction.CONFIRM_EXTERNAL_REJECTED,
                    snapshot.previousStatus(),
                    snapshot.previousStatus(),
                    providerRefundRef,
                    note);
            throw PaymentExceptions.refundConfirmRejected(ex.getMessage());
        } catch (RuntimeException ex) {
            appendAudit(
                    refundId,
                    actorUserId,
                    RefundReconciliationAuditAction.CONFIRM_EXTERNAL_UNCERTAIN,
                    snapshot.previousStatus(),
                    snapshot.previousStatus(),
                    providerRefundRef,
                    note);
            throw PaymentExceptions.refundConfirmUncertain();
        }

        RefundExternalVerify.Result verify = RefundExternalVerify.verify(
                retrieved,
                providerRefundRef,
                snapshot.amountCents(),
                snapshot.currency(),
                snapshot.expectedPaymentIntentId(),
                isStripeTestModeSecretKey());
        if (verify.outcome() == RefundExternalVerify.Outcome.REJECTED) {
            appendAudit(
                    refundId,
                    actorUserId,
                    RefundReconciliationAuditAction.CONFIRM_EXTERNAL_REJECTED,
                    snapshot.previousStatus(),
                    snapshot.previousStatus(),
                    providerRefundRef,
                    note);
            throw PaymentExceptions.refundConfirmRejected(verify.reason());
        }

        transactionTemplate.executeWithoutResult(status -> finalizeConfirmedRefund(
                refundId,
                actorUserId,
                snapshot.previousStatus(),
                retrieved.id(),
                note));
    }

    @Transactional
    public void reopenForIdempotentRetry(UUID refundId, UUID actorUserId) {
        requireActor(actorUserId);
        reopenLocked(refundId, actorUserId, null, false);
    }

    public void reopenAndSubmit(UUID refundId, UUID actorUserId, String note) {
        requireActor(actorUserId);
        try {
            transactionTemplate.executeWithoutResult(status -> reopenLocked(refundId, actorUserId, note, true));
        } catch (BusinessException ex) {
            if ("REFUND_RECONCILIATION_REJECTED".equals(ex.getCode())) {
                appendReopenRejected(refundId, actorUserId, note);
            }
            throw ex;
        }
        refundWorkerService.submit(refundId);
    }

    private ConfirmSnapshot loadConfirmSnapshot(UUID refundId) {
        Refund refund = refundRepository.lockById(refundId).orElse(null);
        if (refund == null) {
            return null;
        }
        if (refund.getStatus() != RefundStatus.RECONCILIATION_REQUIRED
                && refund.getStatus() != RefundStatus.REFUND_SUBMITTED) {
            throw PaymentExceptions.refundConfirmRejected(
                    "Refund cannot be confirmed from status " + refund.getStatus());
        }
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow(PaymentExceptions::notFound);
        if (payment.getProviderRef() == null || payment.getProviderRef().isBlank()) {
            throw PaymentExceptions.refundConfirmRejected("Payment has no provider reference");
        }
        return new ConfirmSnapshot(
                refund.getStatus().name(),
                payment.getProviderRef(),
                refund.getAmountCents(),
                refund.getCurrency());
    }

    private void finalizeConfirmedRefund(
            UUID refundId,
            UUID actorUserId,
            String previousStatus,
            String verifiedProviderRefundRef,
            String note) {
        Refund refund = refundRepository.lockById(refundId).orElseThrow(PaymentExceptions::refundNotFound);
        if (refund.getStatus() == RefundStatus.REFUNDED) {
            return;
        }
        if (refund.getStatus() != RefundStatus.RECONCILIATION_REQUIRED
                && refund.getStatus() != RefundStatus.REFUND_SUBMITTED) {
            throw PaymentExceptions.refundConfirmRejected(
                    "Refund status changed during verification: " + refund.getStatus());
        }
        try {
            refund.confirmExternalRefund(verifiedProviderRefundRef);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw PaymentExceptions.refundConfirmRejected(ex.getMessage());
        }
        refundRepository.saveAndFlush(refund);
        appendAudit(
                refundId,
                actorUserId,
                RefundReconciliationAuditAction.CONFIRM_EXTERNAL_SUCCESS,
                previousStatus,
                RefundStatus.REFUNDED.name(),
                verifiedProviderRefundRef,
                note);
        log.info(
                "refund reconciliation confirmed orderId={} paymentId={} refundId={} providerRefundRef={}",
                refund.getOrderId(),
                refund.getPaymentId(),
                refund.getId(),
                verifiedProviderRefundRef);
    }

    private void reopenLocked(UUID refundId, UUID actorUserId, String note, boolean auditOnSuccess) {
        Refund refund = refundRepository.lockById(refundId).orElseThrow(PaymentExceptions::refundNotFound);
        String previous = refund.getStatus().name();
        String idempotencyKey = refund.getIdempotencyKey();
        try {
            refund.reopenPendingForIdempotentRetry();
        } catch (IllegalStateException ex) {
            throw new BusinessException(
                    "REFUND_RECONCILIATION_REJECTED",
                    ex.getMessage(),
                    HttpStatus.CONFLICT);
        }
        refundRepository.saveAndFlush(refund);
        if (auditOnSuccess) {
            appendAudit(
                    refundId,
                    actorUserId,
                    RefundReconciliationAuditAction.REOPEN_AND_SUBMIT,
                    previous,
                    refund.getStatus().name(),
                    refund.getProviderRefundRef(),
                    note);
        }
        log.warn(
                "refund reconciliation reopened for idempotent retry orderId={} paymentId={} refundId={} "
                        + "idempotencyKey={} previousStatus={}",
                refund.getOrderId(),
                refund.getPaymentId(),
                refund.getId(),
                idempotencyKey,
                previous);
    }

    private void appendReopenRejected(UUID refundId, UUID actorUserId, String note) {
        Refund refund = refundRepository.findById(refundId).orElse(null);
        if (refund == null) {
            return;
        }
        appendAudit(
                refundId,
                actorUserId,
                RefundReconciliationAuditAction.REOPEN_REJECTED,
                refund.getStatus().name(),
                refund.getStatus().name(),
                refund.getProviderRefundRef(),
                note);
    }

    private void appendAudit(
            UUID refundId,
            UUID actorUserId,
            RefundReconciliationAuditAction action,
            String previousStatus,
            String newStatus,
            String providerRefundRef,
            String note) {
        transactionTemplate.executeWithoutResult(status -> auditRepository.saveAndFlush(
                new RefundReconciliationAudit(
                        UUID.randomUUID(),
                        refundId,
                        actorUserId,
                        action,
                        previousStatus,
                        newStatus,
                        providerRefundRef,
                        note)));
    }

    private static void requireActor(UUID actorUserId) {
        if (actorUserId == null) {
            throw new BusinessException(
                    "ACTOR_REQUIRED",
                    "Authenticated administrator is required",
                    HttpStatus.UNAUTHORIZED);
        }
    }

    private boolean isStripeTestModeSecretKey() {
        String key = stripeProperties.getSecretKey();
        if (key == null || key.isBlank()) {
            return stripeProperties.isMock();
        }
        return key.startsWith("sk_test_");
    }

    private record ConfirmSnapshot(
            String previousStatus,
            String expectedPaymentIntentId,
            long amountCents,
            String currency) {
    }
}
