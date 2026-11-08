package com.ecommerce.platform.security.ratelimit;

import java.util.UUID;

/**
 * Rate limits for authenticated sensitive write APIs (checkout, pay, admin refunds).
 * Dual account+IP buckets are evaluated atomically when both apply.
 */
public interface ApiRateLimiter {

    void checkCheckout(UUID accountId, String clientIp);

    void checkPay(UUID accountId, String clientIp);

    void checkAdminRefundConfirm(UUID accountId, String clientIp);

    void checkAdminRefundReopen(UUID accountId, String clientIp);
}
