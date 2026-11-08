package com.ecommerce.platform.payment.provider;

public interface PaymentProvider {

    CreatedIntent createPaymentIntent(CreateIntentCommand command);

    CancelOutcome cancelPaymentIntent(String providerRef);

    RefundOutcome createRefund(CreateRefundCommand command);

    VerifiedEvent constructAndVerifyWebhook(String payload, String signatureHeader);

    RetrievedIntent retrieveIntent(String providerRef);

    RetrievedRefund retrieveRefund(String providerRefundRef);
}
