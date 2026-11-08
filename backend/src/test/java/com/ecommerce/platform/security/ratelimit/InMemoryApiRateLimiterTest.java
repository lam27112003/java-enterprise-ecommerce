package com.ecommerce.platform.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryApiRateLimiterTest {

    private RateLimitProperties properties;
    private InMemoryApiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setCheckoutPerAccountPerMinute(2);
        properties.setCheckoutPerIpPerMinute(5);
        limiter = new InMemoryApiRateLimiter(properties);
    }

    @Test
    void allowedRequestIncrementsAccountAndIp() {
        UUID account = UUID.randomUUID();
        String ip = "10.0.0.1";
        limiter.checkCheckout(account, ip);
        assertThat(limiter.currentCount(limiter.accountKey("checkout", account))).isEqualTo(1);
        assertThat(limiter.currentCount(limiter.ipKey("checkout", ip))).isEqualTo(1);
    }

    @Test
    void accountRejectDoesNotIncrementIp() {
        UUID account = UUID.randomUUID();
        String ip = "10.0.0.2";
        limiter.checkCheckout(account, ip);
        limiter.checkCheckout(account, ip);

        int ipBefore = limiter.currentCount(limiter.ipKey("checkout", ip));
        assertThatThrownBy(() -> limiter.checkCheckout(account, ip))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(limiter.currentCount(limiter.ipKey("checkout", ip))).isEqualTo(ipBefore);
        assertThat(limiter.currentCount(limiter.accountKey("checkout", account))).isEqualTo(2);
    }

    @Test
    void ipRejectDoesNotIncrementAccount() {
        properties.setCheckoutPerAccountPerMinute(10);
        properties.setCheckoutPerIpPerMinute(2);
        limiter = new InMemoryApiRateLimiter(properties);

        String ip = "10.0.0.3";
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID a3 = UUID.randomUUID();
        limiter.checkCheckout(a1, ip);
        limiter.checkCheckout(a2, ip);

        int acctBefore = limiter.currentCount(limiter.accountKey("checkout", a3));
        assertThatThrownBy(() -> limiter.checkCheckout(a3, ip))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(limiter.currentCount(limiter.accountKey("checkout", a3))).isEqualTo(acctBefore);
        assertThat(limiter.currentCount(limiter.ipKey("checkout", ip))).isEqualTo(2);
    }

    @Test
    void legitimateRequestsWithinBudgetSucceed() {
        UUID account = UUID.randomUUID();
        limiter.checkCheckout(account, "10.0.0.4");
        limiter.checkCheckout(account, "10.0.0.4");
        assertThat(limiter.currentCount(limiter.accountKey("checkout", account))).isEqualTo(2);
    }
}
