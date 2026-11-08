package com.ecommerce.platform.security.ratelimit;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback rate limiter when Redis is unavailable.
 * Registered by {@link com.ecommerce.platform.config.AuthInfrastructureConfig}.
 */
public class InMemoryAuthRateLimiter implements AuthRateLimiter {

    private final RateLimitProperties properties;
    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    public InMemoryAuthRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public void checkLogin(String clientIp, String email) {
        if (!properties.isEnabled()) {
            return;
        }
        hit("login:ip:" + safe(clientIp), properties.getLoginPerMinute());
        if (email != null && !email.isBlank()) {
            hit("login:email:" + email.toLowerCase(), properties.getLoginPerMinute());
        }
    }

    @Override
    public void checkRegister(String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }
        hit("register:ip:" + safe(clientIp), properties.getRegisterPerMinute());
    }

    @Override
    public void checkRefresh(String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }
        hit("refresh:ip:" + safe(clientIp), properties.getRefreshPerMinute());
    }

    private void hit(String key, int limit) {
        long now = Instant.now().toEpochMilli();
        long windowMs = properties.getWindowSeconds() * 1000L;
        Deque<Long> q = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= windowMs) {
                q.removeFirst();
            }
            if (q.size() >= limit) {
                throw new RateLimitExceededException();
            }
            q.addLast(now);
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
