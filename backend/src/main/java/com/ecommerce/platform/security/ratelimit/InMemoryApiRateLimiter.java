package com.ecommerce.platform.security.ratelimit;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory dual-bucket limiter with the same allow/reject-without-sibling-increment semantics
 * as {@link RedisApiRateLimiter}. Used when Redis is unavailable.
 */
public class InMemoryApiRateLimiter implements ApiRateLimiter {

    private final RateLimitProperties properties;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public InMemoryApiRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public void checkCheckout(UUID accountId, String clientIp) {
        checkDual(
                "checkout",
                accountId,
                clientIp,
                properties.getCheckoutPerAccountPerMinute(),
                properties.getCheckoutPerIpPerMinute());
    }

    @Override
    public void checkPay(UUID accountId, String clientIp) {
        checkDual(
                "pay",
                accountId,
                clientIp,
                properties.getPayPerAccountPerMinute(),
                properties.getPayPerIpPerMinute());
    }

    @Override
    public void checkAdminRefundConfirm(UUID accountId, String clientIp) {
        checkDual(
                "admin-refund-confirm",
                accountId,
                clientIp,
                properties.getAdminRefundConfirmPerAccountPerMinute(),
                properties.getAdminRefundConfirmPerIpPerMinute());
    }

    @Override
    public void checkAdminRefundReopen(UUID accountId, String clientIp) {
        checkDual(
                "admin-refund-reopen",
                accountId,
                clientIp,
                properties.getAdminRefundReopenPerAccountPerMinute(),
                properties.getAdminRefundReopenPerIpPerMinute());
    }

    void checkDual(String route, UUID accountId, String clientIp, int accountLimit, int ipLimit) {
        if (!properties.isEnabled()) {
            return;
        }
        if (accountLimit < 1 || ipLimit < 1 || properties.getWindowSeconds() < 1) {
            return;
        }
        String acctKey = key(route, "acct", requireAccount(accountId));
        String ipKey = key(route, "ip", safe(clientIp));
        long now = Instant.now().toEpochMilli();
        long windowMs = properties.getWindowSeconds() * 1000L;

        synchronized (lock) {
            int acctCount = count(acctKey, now, windowMs);
            int ipCount = count(ipKey, now, windowMs);
            if (acctCount >= accountLimit || ipCount >= ipLimit) {
                throw new RateLimitExceededException();
            }
            record(acctKey, now, windowMs);
            record(ipKey, now, windowMs);
        }
    }

    /** Test helper: current hits in window for a constructed key. */
    public int currentCount(String fullKey) {
        long now = Instant.now().toEpochMilli();
        long windowMs = properties.getWindowSeconds() * 1000L;
        synchronized (lock) {
            return count(fullKey, now, windowMs);
        }
    }

    public String accountKey(String route, UUID accountId) {
        return key(route, "acct", requireAccount(accountId));
    }

    public String ipKey(String route, String clientIp) {
        return key(route, "ip", safe(clientIp));
    }

    private int count(String key, long now, long windowMs) {
        Deque<Long> q = windows.get(key);
        if (q == null) {
            return 0;
        }
        while (!q.isEmpty() && now - q.peekFirst() >= windowMs) {
            q.removeFirst();
        }
        return q.size();
    }

    private void record(String key, long now, long windowMs) {
        Deque<Long> q = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() >= windowMs) {
            q.removeFirst();
        }
        q.addLast(now);
    }

    private static String key(String route, String dimension, String id) {
        return "api:rl:" + route + ":" + dimension + ":" + id;
    }

    private static String requireAccount(UUID accountId) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId required for API rate limit");
        }
        return accountId.toString();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
