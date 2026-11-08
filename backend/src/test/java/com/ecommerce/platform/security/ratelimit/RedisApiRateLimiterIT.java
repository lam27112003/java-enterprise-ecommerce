package com.ecommerce.platform.security.ratelimit;

import com.ecommerce.platform.support.DockerInfrastructure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Redis Lua dual-bucket semantics (T9–T11 / fail-open covered in unit mock test).
 */
@Testcontainers(disabledWithoutDocker = false)
class RedisApiRateLimiterIT {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    private StringRedisTemplate template;
    private RedisApiRateLimiter limiter;
    private RateLimitProperties properties;

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("RedisApiRateLimiterIT");
    }

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setWindowSeconds(60);
        properties.setCheckoutPerAccountPerMinute(2);
        properties.setCheckoutPerIpPerMinute(5);
        limiter = new RedisApiRateLimiter(template, properties);
    }

    @Test
    void allowedIncrementsAccountAndIp() {
        UUID account = UUID.randomUUID();
        String ip = "203.0.113.10";
        limiter.checkCheckout(account, ip);
        assertThat(template.opsForValue().get(limiter.accountKey("checkout", account))).isEqualTo("1");
        assertThat(template.opsForValue().get(limiter.ipKey("checkout", ip))).isEqualTo("1");
    }

    @Test
    void accountRejectDoesNotIncrementIp() {
        UUID account = UUID.randomUUID();
        String ip = "203.0.113.11";
        limiter.checkCheckout(account, ip);
        limiter.checkCheckout(account, ip);
        String ipBefore = template.opsForValue().get(limiter.ipKey("checkout", ip));
        assertThatThrownBy(() -> limiter.checkCheckout(account, ip))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(template.opsForValue().get(limiter.ipKey("checkout", ip))).isEqualTo(ipBefore);
        assertThat(template.opsForValue().get(limiter.accountKey("checkout", account))).isEqualTo("2");
    }

    @Test
    void ipRejectDoesNotIncrementAccount() {
        properties.setCheckoutPerAccountPerMinute(10);
        properties.setCheckoutPerIpPerMinute(2);
        limiter = new RedisApiRateLimiter(template, properties);

        String ip = "203.0.113.12";
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID a3 = UUID.randomUUID();
        limiter.checkCheckout(a1, ip);
        limiter.checkCheckout(a2, ip);

        assertThat(template.opsForValue().get(limiter.accountKey("checkout", a3))).isNull();
        assertThatThrownBy(() -> limiter.checkCheckout(a3, ip))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(template.opsForValue().get(limiter.accountKey("checkout", a3))).isNull();
        assertThat(template.opsForValue().get(limiter.ipKey("checkout", ip))).isEqualTo("2");
    }
}
