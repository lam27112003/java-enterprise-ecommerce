package com.ecommerce.platform.payment.dto;

import com.ecommerce.platform.payment.domain.RefundReconciliationAuditAction;
import com.ecommerce.platform.payment.domain.RefundStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminRefundDtos {

    private AdminRefundDtos() {
    }

    public record RefundSummaryResponse(
            UUID id,
            UUID paymentId,
            UUID orderId,
            RefundStatus status,
            String reasonCode,
            long amountCents,
            String currency,
            String providerRefundRef,
            Instant createdAt,
            Instant refundedAt) {
    }

    public record RefundAuditResponse(
            UUID id,
            UUID actorUserId,
            RefundReconciliationAuditAction action,
            String previousStatus,
            String newStatus,
            String providerRefundRef,
            String note,
            Instant createdAt) {
    }

    public record RefundDetailResponse(
            RefundSummaryResponse refund,
            String idempotencyKey,
            UUID paymentOrderId,
            long paymentAmountCents,
            String paymentCurrency,
            String paymentProviderRef,
            List<RefundAuditResponse> audits) {
    }

    public record ConfirmExternalRefundRequest(
            @NotBlank @Size(max = 255) String providerRefundRef,
            @Size(max = 1000) String note) {
    }

    public record ReopenAndSubmitRequest(@Size(max = 1000) String note) {
    }
}
