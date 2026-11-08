package com.ecommerce.platform.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Redis fixed-window dual-bucket limiter using a Lua script for atomic account+IP evaluation.
 * On Redis failure, fails open (allows request) and logs a warning.
 */
public class RedisApiRateLimiter implements ApiRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisApiRateLimiter.class);
    private static final String PREFIX = "api:rl:";

    /**
     * KEYS[1]=accountKey, KEYS[2]=ipKey
     * ARGV[1]=accountLimit, ARGV[2]=ipLimit, ARGV[3]=windowSeconds
     * Returns 1 if allowed (both incremented), 0 if rejected (no increments).
     */
    private static final DefaultRedisScript<Long> DUAL_BUCKET_SCRIPT = new DefaultRedisScript<>();

    static {
        DUAL_BUCKET_SCRIPT.setResultType(Long.class);
        DUAL_BUCKET_SCRIPT.setScriptText("""
                local acctLimit = tonumber(ARGV[1])
                local ipLimit = tonumber(ARGV[2])
                local window = tonumber(ARGV[3])
                if not acctLimit or not ipLimit or not window or acctLimit < 1 or ipLimit < 1 or window < 1 then
                  return -1
                end
                local acct = tonumber(redis.call('GET', KEYS[1]) or '0')
                local ip = tonumber(redis.call('GET', KEYS[2]) or '0')
                if acct >= acctLimit or ip >= ipLimit then
                  return 0
                end
                local nacct = redis.call('INCR', KEYS[1])
                if nacct == 1 then
                  redis.call('EXPIRE', KEYS[1], window)
                end
                local nip = redis.call('INCR', KEYS[2])
                if nip == 1 then
                  redis.call('EXPIRE', KEYS[2], window)
                end
                return 1
                """);
    }

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RedisApiRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
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
        String acctKey = PREFIX + route + ":acct:" + requireAccount(accountId);
        String ipKey = PREFIX + route + ":ip:" + safe(clientIp);
        int window = properties.getWindowSeconds();
        if (accountLimit < 1 || ipLimit < 1 || window < 1) {
            log.warn("Invalid rate-limit config for route={}; failing open", route);
            return;
        }
        try {
            Long result = redis.execute(
                    DUAL_BUCKET_SCRIPT,
                    List.of(acctKey, ipKey),
                    String.valueOf(accountLimit),
                    String.valueOf(ipLimit),
                    String.valueOf(window));
            if (result == null || result == -1L) {
                log.warn("Rate limiter script rejected args for route={}; failing open", route);
                return;
            }
            if (result == 0L) {
                throw new RateLimitExceededException();
            }
        } catch (RateLimitExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Rate limiter unavailable; failing open for keys={}", Arrays.asList(acctKey, ipKey), ex);
        }
    }

    /** Exposed for Redis ITs asserting counter state. */
    public String accountKey(String route, UUID accountId) {
        return PREFIX + route + ":acct:" + requireAccount(accountId);
    }

    public String ipKey(String route, String clientIp) {
        return PREFIX + route + ":ip:" + safe(clientIp);
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
