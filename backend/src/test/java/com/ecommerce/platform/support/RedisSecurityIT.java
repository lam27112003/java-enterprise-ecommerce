package com.ecommerce.platform.support;

import com.ecommerce.platform.security.ratelimit.AuthRateLimiter;
import com.ecommerce.platform.security.ratelimit.RateLimitExceededException;
import com.ecommerce.platform.security.ratelimit.RateLimitProperties;
import com.ecommerce.platform.security.ratelimit.RedisAuthRateLimiter;
import com.ecommerce.platform.security.token.AccessTokenDenylist;
import com.ecommerce.platform.security.token.DenylistUnavailableException;
import com.ecommerce.platform.security.token.RedisAccessTokenDenylist;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real Redis via Testcontainers.
 * <p>
 * Documented Redis failure behavior:
 * <ul>
 *   <li>Access-token denylist check: fail-closed ({@link DenylistUnavailableException} / HTTP 503)</li>
 *   <li>Denylist revoke persist failure: throws IllegalStateException</li>
 *   <li>Rate limiter: fail-open (allows request, logs warning)</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisSecurityIT {

    private static final String REDIS_IMAGE = "redis:7.2-alpine";
    private static final String DENYLIST_PREFIX = "auth:access:denylist:";

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:redis_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/testdata");
        registry.add("app.kafka.enabled", () -> "false");
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
        registry.add("management.health.redis.enabled", () -> "true");
        registry.add("app.security.jwt.secret", () -> "integration-redis-secret-must-be-32b-min-ok");
        registry.add("app.security.jwt.issuer", () -> "ecommerce-platform-redis-it");
        registry.add("app.security.rate-limit.enabled", () -> "true");
        registry.add("app.security.rate-limit.register-per-minute", () -> "2");
        registry.add("app.security.rate-limit.login-per-minute", () -> "2");
        registry.add("app.security.rate-limit.refresh-per-minute", () -> "2");
        registry.add("app.security.rate-limit.window-seconds", () -> "60");
        registry.add("app.security.api-docs-public", () -> "true");
        registry.add("app.security.actuator-prometheus-public", () -> "true");
        registry.add("app.security.enforce-secure-jwt-secret", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private AccessTokenDenylist denylist;
    @Autowired
    private AuthRateLimiter rateLimiter;

    @BeforeAll
    static void requireDockerAndContainers() {
        DockerInfrastructure.requireDocker("RedisSecurityIT");
    }

    @org.junit.jupiter.api.BeforeEach
    void requireRedisBackedBeans() {
        assertThat(denylist)
                .as("AccessTokenDenylist must be Redis-backed when Redis Testcontainers is running")
                .isInstanceOf(RedisAccessTokenDenylist.class);
        assertThat(rateLimiter)
                .as("AuthRateLimiter must be Redis-backed when Redis Testcontainers is running")
                .isInstanceOf(RedisAuthRateLimiter.class);
    }

    @Test
    @Order(1)
    void logoutStoresRevocationInRedisAndRejectsToken() throws Exception {
        JsonNode tokens = register("redis-logout-" + UUID.randomUUID() + "@example.com");
        String access = tokens.path("accessToken").asText();
        String refresh = tokens.path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s" }
                                """.formatted(refresh)))
                .andExpect(status().isOk());

        assertThat(redisTemplate.keys(DENYLIST_PREFIX + "*")).isNotEmpty();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @Order(2)
    void denylistSurvivesClientRestartWhileRedisPreserved() {
        String jti = "restart-jti-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(120);
        denylist.revoke(jti, expiresAt);
        assertThat(denylist.isRevoked(jti)).isTrue();
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(DENYLIST_PREFIX + jti))).isTrue();

        // Simulate application restart: new Redis client + denylist bean against same Redis data.
        LettuceConnectionFactory factory = lettuceFactory(redis.getHost(), redis.getMappedPort(6379), Duration.ofSeconds(2));
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate restarted = new StringRedisTemplate(factory);
            restarted.afterPropertiesSet();
            RedisAccessTokenDenylist restartedDenylist = new RedisAccessTokenDenylist(restarted);
            assertThat(restartedDenylist.isRevoked(jti)).isTrue();
            assertThat(Boolean.TRUE.equals(restarted.hasKey(DENYLIST_PREFIX + jti))).isTrue();
        } finally {
            factory.destroy();
        }
    }

    @Test
    @Order(3)
    void denylistTtlMatchesAccessTokenExpiry() throws InterruptedException {
        String jti = "ttl-jti-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3);
        denylist.revoke(jti, expiresAt);

        Long ttl = redisTemplate.getExpire(DENYLIST_PREFIX + jti, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isBetween(1L, 3L);
        assertThat(denylist.isRevoked(jti)).isTrue();

        Thread.sleep(3500);
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey(DENYLIST_PREFIX + jti))).isFalse();
        assertThat(denylist.isRevoked(jti)).isFalse();
    }

    @Test
    @Order(4)
    void rateLimitingWorksThroughRedis() throws Exception {
        String ip = "198.51.100." + (10 + Math.abs(UUID.randomUUID().hashCode() % 40));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .with(request -> {
                                request.setRemoteAddr(ip);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "redis-rl-%s-%d@example.com",
                                      "password": "Str0ng!Pass",
                                      "fullName": "Redis RL"
                                    }
                                    """.formatted(UUID.randomUUID(), i)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "redis-rl-overflow-%s@example.com",
                                  "password": "Str0ng!Pass",
                                  "fullName": "Redis RL"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @Order(5)
    void multipleInstancesShareRateLimitsViaRedis() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setRegisterPerMinute(2);
        properties.setWindowSeconds(60);

        LettuceConnectionFactory factory = lettuceFactory(redis.getHost(), redis.getMappedPort(6379), Duration.ofSeconds(2));
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate shared = new StringRedisTemplate(factory);
            shared.afterPropertiesSet();
            RedisAuthRateLimiter instanceA = new RedisAuthRateLimiter(shared, properties);
            RedisAuthRateLimiter instanceB = new RedisAuthRateLimiter(shared, properties);

            String ip = "203.0.113." + (50 + Math.abs(UUID.randomUUID().hashCode() % 40));
            instanceA.checkRegister(ip);
            instanceB.checkRegister(ip);
            assertThatThrownBy(() -> instanceA.checkRegister(ip))
                    .isInstanceOf(RateLimitExceededException.class);
            assertThatThrownBy(() -> instanceB.checkRegister(ip))
                    .isInstanceOf(RateLimitExceededException.class);
        } finally {
            factory.destroy();
        }
    }

    @Test
    @Order(6)
    void redisOutageFailsClosedForDenylistAndOpenForRateLimit() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setLoginPerMinute(1);
        properties.setWindowSeconds(60);

        LettuceConnectionFactory factory = lettuceFactory(redis.getHost(), redis.getMappedPort(6379), Duration.ofMillis(400));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        RedisAccessTokenDenylist isolatedDenylist = new RedisAccessTokenDenylist(template);
        RedisAuthRateLimiter isolatedLimiter = new RedisAuthRateLimiter(template, properties);

        String jti = "outage-jti-" + UUID.randomUUID();
        isolatedDenylist.revoke(jti, Instant.now().plusSeconds(60));

        redis.stop();
        try {
            assertThatThrownBy(() -> isolatedDenylist.isRevoked(jti))
                    .isInstanceOf(DenylistUnavailableException.class);
            assertThatThrownBy(() -> isolatedDenylist.revoke("another-" + UUID.randomUUID(), Instant.now().plusSeconds(30)))
                    .isInstanceOf(IllegalStateException.class);
            assertThatCode(() -> isolatedLimiter.checkLogin("10.0.0.1", "outage@example.com"))
                    .doesNotThrowAnyException();
        } finally {
            factory.destroy();
            // Restart for any later ordered tests / JVM teardown cleanliness.
            redis.start();
        }
    }

    private static LettuceConnectionFactory lettuceFactory(String host, int port, Duration timeout) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .build();
        return new LettuceConnectionFactory(standalone, client);
    }

    private JsonNode register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Str0ng!Pass",
                                  "fullName": "Redis IT User"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
