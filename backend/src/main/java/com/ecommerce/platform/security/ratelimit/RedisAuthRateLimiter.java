package com.ecommerce.platform.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis fixed-window counters. On Redis failure, fails open (allows request) and logs a warning
 * so a cache outage does not permanently block authentication.
 * Registered by {@link com.ecommerce.platform.config.AuthInfrastructureConfig}.
 */
public class RedisAuthRateLimiter implements AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisAuthRateLimiter.class);
    private static final String PREFIX = "auth:rl:";

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RedisAuthRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void checkLogin(String clientIp, String email) {
        if (!properties.isEnabled()) {
            return;
        }
        incrementOrThrow(PREFIX + "login:ip:" + safe(clientIp), properties.getLoginPerMinute());
        if (email != null && !email.isBlank()) {
            incrementOrThrow(PREFIX + "login:email:" + email.toLowerCase(), properties.getLoginPerMinute());
        }
    }

    @Override
    public void checkRegister(String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }
        incrementOrThrow(PREFIX + "register:ip:" + safe(clientIp), properties.getRegisterPerMinute());
    }

    @Override
    public void checkRefresh(String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }
        incrementOrThrow(PREFIX + "refresh:ip:" + safe(clientIp), properties.getRefreshPerMinute());
    }

    private void incrementOrThrow(String key, int limit) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(properties.getWindowSeconds()));
            }
            if (count != null && count > limit) {
                throw new RateLimitExceededException();
            }
        } catch (RateLimitExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable; failing open for key={}", key, ex);
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
