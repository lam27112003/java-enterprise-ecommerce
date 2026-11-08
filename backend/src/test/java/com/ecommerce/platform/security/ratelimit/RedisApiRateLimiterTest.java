package com.ecommerce.platform.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisApiRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    private RateLimitProperties properties;
    private RedisApiRateLimiter limiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setCheckoutPerAccountPerMinute(2);
        properties.setCheckoutPerIpPerMinute(5);
        limiter = new RedisApiRateLimiter(redis, properties);
    }

    @Test
    void allowedWhenScriptReturnsOne() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        assertThatCode(() -> limiter.checkCheckout(UUID.randomUUID(), "10.1.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenScriptReturnsZero() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(0L);
        assertThatThrownBy(() -> limiter.checkCheckout(UUID.randomUUID(), "10.1.0.2"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void failsOpenWhenRedisThrows() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));
        assertThatCode(() -> limiter.checkCheckout(UUID.randomUUID(), "10.1.0.3"))
                .doesNotThrowAnyException();
    }

    @Test
    void failsOpenWhenScriptReturnsInvalidArgsCode() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(-1L);
        assertThatCode(() -> limiter.checkCheckout(UUID.randomUUID(), "10.1.0.4"))
                .doesNotThrowAnyException();
    }
}
