package com.ecommerce.platform.payment.service;

import com.ecommerce.platform.outbox.domain.OutboxEventTypes;
import com.ecommerce.platform.outbox.service.OutboxService;
import com.ecommerce.platform.payment.domain.Payment;
import com.ecommerce.platform.payment.domain.Refund;
import com.ecommerce.platform.payment.domain.RefundReasons;
import com.ecommerce.platform.payment.repository.PaymentRepository;
import com.ecommerce.platform.payment.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Books a single refund row and outbox message inside the caller's transaction.
 * Stripe is not called here.
 */
@Service
public class RefundBookingService {

    private static final Logger log = LoggerFactory.getLogger(RefundBookingService.class);

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OutboxService outboxService;

    public RefundBookingService(
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            OutboxService outboxService) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.outboxService = outboxService;
    }

    public Refund bookLateSuccess(Payment payment, UUID orderId) {
        payment.markSucceededNeedsRefund();
        paymentRepository.saveAndFlush(payment);
        Optional<Refund> existing = refundRepository.findByPaymentIdAndReasonCode(
                payment.getId(), RefundReasons.LATE_SUCCESS);
        if (existing.isPresent()) {
            return existing.get();
        }
        Refund refund = new Refund(
                UUID.randomUUID(),
                payment.getId(),
                orderId,
                payment.getAmountCents(),
                payment.getCurrency(),
                RefundReasons.LATE_SUCCESS,
                RefundReasons.idempotencyKey(payment.getId(), RefundReasons.LATE_SUCCESS));
        refundRepository.saveAndFlush(refund);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("paymentId", payment.getId().toString());
        payload.put("refundId", refund.getId().toString());
        payload.put("providerRef", payment.getProviderRef());
        payload.put("reasonCode", refund.getReasonCode());
        payload.put("amountCents", payment.getAmountCents());
        payload.put("currency", payment.getCurrency());
        outboxService.save("refund", refund.getId().toString(), OutboxEventTypes.REFUND_REQUESTED, payload);
        log.info(
                "refund booked orderId={} paymentId={} providerRef={} refundId={} reasonCode={}",
                orderId,
                payment.getId(),
                payment.getProviderRef(),
                refund.getId(),
                refund.getReasonCode());
        return refund;
    }
}
