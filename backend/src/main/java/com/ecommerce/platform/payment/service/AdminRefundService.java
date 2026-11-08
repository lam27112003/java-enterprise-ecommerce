package com.ecommerce.platform.payment.service;

import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.payment.domain.Payment;
import com.ecommerce.platform.payment.domain.Refund;
import com.ecommerce.platform.payment.domain.RefundReconciliationAudit;
import com.ecommerce.platform.payment.domain.RefundStatus;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.ConfirmExternalRefundRequest;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.ReopenAndSubmitRequest;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.RefundAuditResponse;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.RefundDetailResponse;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.RefundSummaryResponse;
import com.ecommerce.platform.payment.exception.PaymentExceptions;
import com.ecommerce.platform.payment.repository.PaymentRepository;
import com.ecommerce.platform.payment.repository.RefundReconciliationAuditRepository;
import com.ecommerce.platform.payment.repository.RefundRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminRefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundReconciliationAuditRepository auditRepository;
    private final RefundReconciliationService refundReconciliationService;

    public AdminRefundService(
            RefundRepository refundRepository,
            PaymentRepository paymentRepository,
            RefundReconciliationAuditRepository auditRepository,
            RefundReconciliationService refundReconciliationService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.auditRepository = auditRepository;
        this.refundReconciliationService = refundReconciliationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundSummaryResponse> list(RefundStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Refund> result = status == null
                ? refundRepository.findAll(pageable)
                : refundRepository.findByStatus(status, pageable);
        return PageResponse.from(result.map(AdminRefundService::toSummary));
    }

    @Transactional(readOnly = true)
    public RefundDetailResponse getDetail(UUID refundId) {
        Refund refund = refundRepository.findById(refundId).orElseThrow(PaymentExceptions::refundNotFound);
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow(PaymentExceptions::notFound);
        List<RefundAuditResponse> audits = auditRepository.findByRefundIdOrderByCreatedAtAsc(refundId).stream()
                .map(AdminRefundService::toAudit)
                .toList();
        return new RefundDetailResponse(
                toSummary(refund),
                refund.getIdempotencyKey(),
                payment.getOrderId(),
                payment.getAmountCents(),
                payment.getCurrency(),
                payment.getProviderRef(),
                audits);
    }

    @Transactional(readOnly = true)
    public List<RefundAuditResponse> listAudits(UUID refundId) {
        if (!refundRepository.existsById(refundId)) {
            throw PaymentExceptions.refundNotFound();
        }
        return auditRepository.findByRefundIdOrderByCreatedAtAsc(refundId).stream()
                .map(AdminRefundService::toAudit)
                .toList();
    }

    public void confirmExternal(UUID refundId, UUID actorUserId, ConfirmExternalRefundRequest request) {
        refundReconciliationService.confirmExternalRefund(
                refundId,
                actorUserId,
                request.providerRefundRef(),
                request.note());
    }

    public void reopenAndSubmit(UUID refundId, UUID actorUserId, ReopenAndSubmitRequest request) {
        String note = request == null ? null : request.note();
        refundReconciliationService.reopenAndSubmit(refundId, actorUserId, note);
    }

    private static RefundSummaryResponse toSummary(Refund refund) {
        return new RefundSummaryResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getOrderId(),
                refund.getStatus(),
                refund.getReasonCode(),
                refund.getAmountCents(),
                refund.getCurrency(),
                refund.getProviderRefundRef(),
                refund.getCreatedAt(),
                refund.getRefundedAt());
    }

    private static RefundAuditResponse toAudit(RefundReconciliationAudit audit) {
        return new RefundAuditResponse(
                audit.getId(),
                audit.getActorUserId(),
                audit.getAction(),
                audit.getPreviousStatus(),
                audit.getNewStatus(),
                audit.getProviderRefundRef(),
                audit.getNote(),
                audit.getCreatedAt());
    }
}
