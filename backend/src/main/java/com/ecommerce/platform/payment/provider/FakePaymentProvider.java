package com.ecommerce.platform.payment.provider;

import com.ecommerce.platform.payment.exception.PaymentExceptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory PaymentProvider for tests and {@code app.stripe.mock=true}.
 * Webhook signatures must equal {@code test_sig}. Client secrets stay in memory only.
 */
@Component
@ConditionalOnProperty(prefix = "app.stripe", name = "mock", havingValue = "true")
public class FakePaymentProvider implements PaymentProvider {

    public static final String TEST_SIGNATURE = "test_sig";

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, StoredIntent> intentsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idempotencyToId = new ConcurrentHashMap<>();
    private final AtomicBoolean nextRefundUncertain = new AtomicBoolean(false);
    private final AtomicBoolean nextRetrieveRefundUncertain = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, RetrievedRefund> refundsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> chargeToPaymentIntent = new ConcurrentHashMap<>();

    public FakePaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Next {@link #createRefund} returns an uncertain outcome (P6-D7 reconciliation path). */
    public void failNextRefundAsUncertain() {
        nextRefundUncertain.set(true);
    }

    /** Next {@link #retrieveRefund} throws an uncertain provider error. */
    public void failNextRetrieveRefundAsUncertain() {
        nextRetrieveRefundUncertain.set(true);
    }

    /** Script a refund snapshot for reconciliation tests (P7-D10). */
    public void scriptRefund(RetrievedRefund refund) {
        if (refund == null || refund.id() == null) {
            throw new IllegalArgumentException("refund id is required");
        }
        refundsById.put(refund.id(), refund);
    }

    /** Charge-only association path: {@code Charge.retrieve} resolves PI via this map. */
    public void scriptChargePaymentIntent(String chargeId, String paymentIntentId) {
        chargeToPaymentIntent.put(chargeId, paymentIntentId);
    }

    @Override
    public CreatedIntent createPaymentIntent(CreateIntentCommand command) {
        String existingId = idempotencyToId.get(command.idempotencyKey());
        if (existingId != null) {
            StoredIntent existing = intentsById.get(existingId);
            return new CreatedIntent(existing.id, existing.clientSecret, existing.status);
        }
        String id = "pi_" + UUID.randomUUID().toString().replace("-", "");
        String secret = "cs_test_" + id;
        StoredIntent stored = new StoredIntent(
                id,
                secret,
                "requires_action",
                command.amountCents(),
                command.currency());
        intentsById.put(id, stored);
        idempotencyToId.put(command.idempotencyKey(), id);
        return new CreatedIntent(id, secret, stored.status);
    }

    @Override
    public CancelOutcome cancelPaymentIntent(String providerRef) {
        StoredIntent intent = intentsById.get(providerRef);
        if (intent == null) {
            return new CancelOutcome("missing", CancelKind.UNCERTAIN);
        }
        if ("succeeded".equals(intent.status)) {
            return new CancelOutcome(intent.status, CancelKind.SUCCEEDED_INSTEAD);
        }
        if ("canceled".equals(intent.status)) {
            return new CancelOutcome(intent.status, CancelKind.CANCELED);
        }
        intent.status = "canceled";
        return new CancelOutcome("canceled", CancelKind.CANCELED);
    }

    @Override
    public RefundOutcome createRefund(CreateRefundCommand command) {
        if (nextRefundUncertain.compareAndSet(true, false)) {
            throw new PaymentProviderException("simulated uncertain refund", null, true);
        }
        StoredIntent intent = intentsById.get(command.providerRef());
        if (intent == null) {
            return new RefundOutcome(null, "missing", RefundOutcomeKind.DEFINITIVE_FAILURE);
        }
        String refundId = "re_" + UUID.randomUUID().toString().replace("-", "");
        refundsById.put(
                refundId,
                new RetrievedRefund(
                        refundId,
                        "succeeded",
                        command.amountCents(),
                        intent.currency,
                        command.providerRef(),
                        null,
                        false));
        return new RefundOutcome(refundId, "succeeded", RefundOutcomeKind.CONFIRMED_SUCCESS);
    }

    @Override
    public RetrievedRefund retrieveRefund(String providerRefundRef) {
        if (nextRetrieveRefundUncertain.compareAndSet(true, false)) {
            throw new PaymentProviderException("simulated uncertain retrieveRefund", null, true);
        }
        RetrievedRefund stored = refundsById.get(providerRefundRef);
        if (stored == null) {
            throw new PaymentProviderException("provider refund not found", null, false);
        }
        if (stored.paymentIntentId() == null
                && stored.chargeId() != null
                && !stored.chargeId().isBlank()) {
            String pi = chargeToPaymentIntent.get(stored.chargeId());
            return new RetrievedRefund(
                    stored.id(),
                    stored.status(),
                    stored.amountCents(),
                    stored.currency(),
                    pi,
                    stored.chargeId(),
                    stored.livemode());
        }
        return stored;
    }

    @Override
    public VerifiedEvent constructAndVerifyWebhook(String payload, String signatureHeader) {
        if (!TEST_SIGNATURE.equals(signatureHeader)) {
            throw PaymentExceptions.invalidSignature();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = text(root, "id");
            String type = text(root, "type");
            if (eventId == null || type == null) {
                throw PaymentExceptions.invalidWebhook();
            }
            JsonNode object = root.path("data").path("object");
            Long amount = object.hasNonNull("amount") ? object.get("amount").asLong() : null;
            return new VerifiedEvent(
                    eventId,
                    type,
                    text(object, "id"),
                    amount,
                    text(object, "currency"),
                    text(object, "status"),
                    text(object, "payment_intent"));
        } catch (PaymentExceptions.Marker ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw PaymentExceptions.invalidWebhook();
        } catch (Exception ex) {
            throw PaymentExceptions.invalidWebhook();
        }
    }

    @Override
    public RetrievedIntent retrieveIntent(String providerRef) {
        StoredIntent intent = intentsById.get(providerRef);
        if (intent == null) {
            throw new PaymentProviderException("provider intent not found", null, false);
        }
        return new RetrievedIntent(
                intent.id,
                intent.clientSecret,
                intent.status,
                intent.amountCents,
                intent.currency);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static final class StoredIntent {
        private final String id;
        private final String clientSecret;
        private volatile String status;
        private final long amountCents;
        private final String currency;

        private StoredIntent(String id, String clientSecret, String status, long amountCents, String currency) {
            this.id = id;
            this.clientSecret = clientSecret;
            this.status = status;
            this.amountCents = amountCents;
            this.currency = currency;
        }
    }
}
