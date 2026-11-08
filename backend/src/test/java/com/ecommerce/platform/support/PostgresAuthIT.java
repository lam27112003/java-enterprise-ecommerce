package com.ecommerce.platform.support;

import com.ecommerce.platform.auth.repository.RefreshTokenRepository;
import com.ecommerce.platform.auth.service.RefreshTokenService;
import com.ecommerce.platform.user.domain.Role;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import com.ecommerce.platform.user.repository.RoleRepository;
import com.ecommerce.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real PostgreSQL via Testcontainers + Failsafe.
 * Requires a running Docker engine; fails hard (does not skip) when unavailable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = false)
class PostgresAuthIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("PostgresAuthIT");
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/migration_postgresql,classpath:db/testdata");
        registry.add("app.kafka.enabled", () -> "false");
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("app.security.jwt.secret", () -> "integration-postgres-secret-must-be-32b-min");
        registry.add("app.security.jwt.issuer", () -> "ecommerce-platform-pg-it");
        registry.add("app.security.rate-limit.enabled", () -> "true");
        registry.add("app.security.rate-limit.login-per-minute", () -> "1000");
        registry.add("app.security.rate-limit.register-per-minute", () -> "1000");
        registry.add("app.security.rate-limit.refresh-per-minute", () -> "1000");
        registry.add("app.security.api-docs-public", () -> "true");
        registry.add("app.security.actuator-prometheus-public", () -> "true");
        registry.add("app.security.enforce-secure-jwt-secret", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void flywayMigrationsAppliedOnRealPostgres() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);
        assertThat(migrationCount).isGreaterThanOrEqualTo(3);

        Integer platformMeta = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schema_meta WHERE schema_key = 'platform.phase'",
                Integer.class);
        assertThat(platformMeta).isEqualTo(1);

        Integer roles = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
        assertThat(roles).isGreaterThanOrEqualTo(2);
    }

    @Test
    void authenticationRepositoryOperationsPersist() {
        Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
        String email = "pg-repo-" + UUID.randomUUID() + "@example.com";
        User user = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode("Str0ng!Pass"),
                "PG User",
                UserStatus.ACTIVE
        );
        user.addRole(customer);
        userRepository.saveAndFlush(user);

        User loaded = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(loaded.getRoles()).extracting(Role::getCode).contains(RoleCode.CUSTOMER);

        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(loaded);
        assertThat(refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(issued.rawToken()))).isPresent();
        assertThat(refreshTokenRepository.countByFamilyIdAndRevokedAtIsNull(issued.entity().getFamilyId()))
                .isEqualTo(1);
    }

    @Test
    void concurrentRefreshRotationUsesRowLocking() throws Exception {
        JsonNode tokens = register("pg-concurrent-" + UUID.randomUUID() + "@example.com");
        String refresh = tokens.path("refreshToken").asText();
        String hash = RefreshTokenService.hash(refresh);
        UUID familyId = refreshTokenRepository.findByTokenHash(hash).orElseThrow().getFamilyId();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            { "refreshToken": "%s" }
                                            """.formatted(refresh)))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        success.incrementAndGet();
                    } else {
                        failure.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failure.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get(45, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(threads - 1);
        assertThat(refreshTokenRepository.countByFamilyIdAndRevokedAtIsNull(familyId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s" }
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void transactionRollbackDoesNotPersistUser() {
        String email = "pg-rollback-" + UUID.randomUUID() + "@example.com";
        Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();

        transactionTemplate.executeWithoutResult(status -> {
            User user = new User(
                    UUID.randomUUID(),
                    email,
                    passwordEncoder.encode("Str0ng!Pass"),
                    "Rollback User",
                    UserStatus.ACTIVE
            );
            user.addRole(customer);
            userRepository.saveAndFlush(user);
            assertThat(userRepository.findByEmailIgnoreCase(email)).isPresent();
            status.setRollbackOnly();
        });

        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    private JsonNode register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Str0ng!Pass",
                                  "fullName": "PG IT User"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
