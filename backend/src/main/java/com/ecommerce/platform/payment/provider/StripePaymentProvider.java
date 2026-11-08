package com.ecommerce.platform.payment.provider;

import com.ecommerce.platform.config.StripeProperties;
import com.ecommerce.platform.payment.exception.PaymentExceptions;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Stripe Test Mode PaymentIntents. Callers must not invoke these methods inside an open
 * database transaction. Card numbers are never accepted or stored.
 */
@Component
@ConditionalOnProperty(prefix = "app.stripe", name = "mock", havingValue = "false", matchIfMissing = true)
public class StripePaymentProvider implements PaymentProvider {

    private final StripeProperties properties;

    public StripePaymentProvider(StripeProperties properties) {
        this.properties = properties;
    }

    @Override
    public CreatedIntent createPaymentIntent(CreateIntentCommand command) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(command.amountCents())
                    .setCurrency(command.currency().toLowerCase(Locale.ROOT))
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .putMetadata("orderId", command.orderId().toString())
                    .putMetadata("paymentId", command.paymentId().toString())
                    .build();
            PaymentIntent intent = PaymentIntent.create(params, options(command.idempotencyKey()));
            return new CreatedIntent(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public CancelOutcome cancelPaymentIntent(String providerRef) {
        try {
            PaymentIntent current = PaymentIntent.retrieve(providerRef, options(null));
            if ("succeeded".equals(current.getStatus())) {
                return new CancelOutcome(current.getStatus(), CancelKind.SUCCEEDED_INSTEAD);
            }
            if ("canceled".equals(current.getStatus())) {
                return new CancelOutcome(current.getStatus(), CancelKind.CANCELED);
            }
            PaymentIntent canceled = current.cancel(PaymentIntentCancelParams.builder().build(), options(null));
            if ("succeeded".equals(canceled.getStatus())) {
                return new CancelOutcome(canceled.getStatus(), CancelKind.SUCCEEDED_INSTEAD);
            }
            if ("canceled".equals(canceled.getStatus())) {
                return new CancelOutcome(canceled.getStatus(), CancelKind.CANCELED);
            }
            return new CancelOutcome(canceled.getStatus(), CancelKind.UNCERTAIN);
        } catch (StripeException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public RefundOutcome createRefund(CreateRefundCommand command) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(command.providerRef())
                    .setAmount(command.amountCents())
                    .build();
            Refund refund = Refund.create(params, options(command.idempotencyKey()));
            return new RefundOutcome(refund.getId(), refund.getStatus(), classifyRefund(refund.getStatus()));
        } catch (StripeException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public VerifiedEvent constructAndVerifyWebhook(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()
                || properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            throw PaymentExceptions.invalidSignature();
        }
        final Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, properties.getWebhookSecret());
        } catch (SignatureVerificationException ex) {
            throw PaymentExceptions.invalidSignature();
        } catch (Exception ex) {
            throw PaymentExceptions.invalidWebhook();
        }
        StripeObject stripeObject = deserialize(event);
        if (stripeObject instanceof PaymentIntent intent) {
            return new VerifiedEvent(
                    event.getId(),
                    event.getType(),
                    intent.getId(),
                    intent.getAmount(),
                    intent.getCurrency(),
                    intent.getStatus(),
                    intent.getId());
        }
        if (stripeObject instanceof Refund refund) {
            return new VerifiedEvent(
                    event.getId(),
                    event.getType(),
                    refund.getId(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    refund.getStatus(),
                    refund.getPaymentIntent());
        }
        return new VerifiedEvent(event.getId(), event.getType(), null, null, null, null, null);
    }

    @Override
    public RetrievedIntent retrieveIntent(String providerRef) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(providerRef, options(null));
            return new RetrievedIntent(
                    intent.getId(),
                    intent.getClientSecret(),
                    intent.getStatus(),
                    intent.getAmount(),
                    intent.getCurrency());
        } catch (StripeException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public RetrievedRefund retrieveRefund(String providerRefundRef) {
        try {
            Refund refund = Refund.retrieve(providerRefundRef, options(null));
            String paymentIntentId = refund.getPaymentIntent();
            String chargeId = refund.getCharge();
            if ((paymentIntentId == null || paymentIntentId.isBlank())
                    && chargeId != null
                    && !chargeId.isBlank()) {
                Charge charge = Charge.retrieve(chargeId, options(null));
                paymentIntentId = charge.getPaymentIntent();
            }
            return new RetrievedRefund(
                    refund.getId(),
                    refund.getStatus(),
                    refund.getAmount(),
                    refund.getCurrency(),
                    paymentIntentId,
                    chargeId,
                    null);
        } catch (StripeException ex) {
            throw wrap(ex);
        }
    }

    private RequestOptions options(String idempotencyKey) {
        RequestOptions.RequestOptionsBuilder builder = RequestOptions.builder();
        if (properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
            builder.setApiKey(properties.getSecretKey());
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            builder.setIdempotencyKey(idempotencyKey);
        }
        return builder.build();
    }

    private static StripeObject deserialize(Event event) {
        var deserializer = event.getDataObjectDeserializer();
        if (deserializer.getObject().isPresent()) {
            return deserializer.getObject().get();
        }
        try {
            return deserializer.deserializeUnsafe();
        } catch (Exception ex) {
            return null;
        }
    }

    private static RefundOutcomeKind classifyRefund(String status) {
        if ("succeeded".equalsIgnoreCase(status)) {
            return RefundOutcomeKind.CONFIRMED_SUCCESS;
        }
        if ("failed".equalsIgnoreCase(status) || "canceled".equalsIgnoreCase(status)) {
            return RefundOutcomeKind.DEFINITIVE_FAILURE;
        }
        return RefundOutcomeKind.UNCERTAIN;
    }

    private static PaymentProviderException wrap(StripeException ex) {
        Integer status = ex.getStatusCode();
        boolean uncertain = status == null || status == 408 || status == 409 || status == 429 || status >= 500;
        return new PaymentProviderException("payment provider request failed", ex, uncertain);
    }
}
