package com.ecommerce.platform.security.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.UUID;

/**
 * Observational wrapper: counts rate-limit rejections by low-cardinality bucket name only.
 * Never used as payment/order status authority.
 */
public class MeteredApiRateLimiter implements ApiRateLimiter {

    private final ApiRateLimiter delegate;
    private final MeterRegistry meterRegistry;

    public MeteredApiRateLimiter(ApiRateLimiter delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void checkCheckout(UUID accountId, String clientIp) {
        guard("checkout", () -> delegate.checkCheckout(accountId, clientIp));
    }

    @Override
    public void checkPay(UUID accountId, String clientIp) {
        guard("pay", () -> delegate.checkPay(accountId, clientIp));
    }

    @Override
    public void checkAdminRefundConfirm(UUID accountId, String clientIp) {
        guard("admin_refund_confirm", () -> delegate.checkAdminRefundConfirm(accountId, clientIp));
    }

    @Override
    public void checkAdminRefundReopen(UUID accountId, String clientIp) {
        guard("admin_refund_reopen", () -> delegate.checkAdminRefundReopen(accountId, clientIp));
    }

    private void guard(String bucket, Runnable action) {
        try {
            action.run();
        } catch (RateLimitExceededException ex) {
            meterRegistry.counter("rate_limit_rejections", "bucket", bucket, "outcome", "rejected").increment();
            throw ex;
        }
    }
}
