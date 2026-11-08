package com.ecommerce.platform.config;

import com.ecommerce.platform.security.ratelimit.ApiRateLimiter;
import com.ecommerce.platform.security.ratelimit.AuthRateLimiter;
import com.ecommerce.platform.security.ratelimit.InMemoryApiRateLimiter;
import com.ecommerce.platform.security.ratelimit.InMemoryAuthRateLimiter;
import com.ecommerce.platform.security.ratelimit.MeteredApiRateLimiter;
import com.ecommerce.platform.security.ratelimit.RateLimitProperties;
import com.ecommerce.platform.security.ratelimit.RedisApiRateLimiter;
import com.ecommerce.platform.security.ratelimit.RedisAuthRateLimiter;
import com.ecommerce.platform.security.token.AccessTokenDenylist;
import com.ecommerce.platform.security.token.InMemoryAccessTokenDenylist;
import com.ecommerce.platform.security.token.RedisAccessTokenDenylist;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Selects Redis-backed auth infrastructure when a {@link RedisConnectionFactory} is available.
 * Uses {@link ObjectProvider} at bean-creation time so selection is not subject to the early
 * {@code @ConditionalOnBean} evaluation race against {@code RedisAutoConfiguration}.
 */
@Configuration
public class AuthInfrastructureConfig {

    @Bean
    public AccessTokenDenylist accessTokenDenylist(
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory,
            ObjectProvider<StringRedisTemplate> stringRedisTemplate) {
        if (redisConnectionFactory.getIfAvailable() != null) {
            StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
            if (redis != null) {
                return new RedisAccessTokenDenylist(redis);
            }
        }
        return new InMemoryAccessTokenDenylist();
    }

    @Bean
    public AuthRateLimiter authRateLimiter(
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory,
            ObjectProvider<StringRedisTemplate> stringRedisTemplate,
            RateLimitProperties properties) {
        if (redisConnectionFactory.getIfAvailable() != null) {
            StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
            if (redis != null) {
                return new RedisAuthRateLimiter(redis, properties);
            }
        }
        return new InMemoryAuthRateLimiter(properties);
    }

    @Bean
    public ApiRateLimiter apiRateLimiter(
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory,
            ObjectProvider<StringRedisTemplate> stringRedisTemplate,
            RateLimitProperties properties,
            MeterRegistry meterRegistry) {
        ApiRateLimiter delegate;
        if (redisConnectionFactory.getIfAvailable() != null) {
            StringRedisTemplate redis = stringRedisTemplate.getIfAvailable();
            if (redis != null) {
                delegate = new RedisApiRateLimiter(redis, properties);
            } else {
                delegate = new InMemoryApiRateLimiter(properties);
            }
        } else {
            delegate = new InMemoryApiRateLimiter(properties);
        }
        return new MeteredApiRateLimiter(delegate, meterRegistry);
    }
}
