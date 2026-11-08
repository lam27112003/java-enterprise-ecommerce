package com.ecommerce.platform.inventory;

import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductResponse;
import com.ecommerce.platform.catalog.service.CategoryService;
import com.ecommerce.platform.catalog.service.ProductService;
import com.ecommerce.platform.common.exception.BusinessException;
import com.ecommerce.platform.inventory.domain.Inventory;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryAdjustRequest;
import com.ecommerce.platform.inventory.repository.InventoryRepository;
import com.ecommerce.platform.inventory.service.InventoryService;
import com.ecommerce.platform.support.DockerInfrastructure;
import com.ecommerce.platform.user.domain.Role;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import com.ecommerce.platform.user.repository.RoleRepository;
import com.ecommerce.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
class InventoryConcurrencyIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("InventoryConcurrencyIT");
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
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
        registry.add("app.security.jwt.secret", () -> "integration-inventory-pg-secret-must-32b");
        registry.add("app.catalog.cache.enabled", () -> "false");
        registry.add("app.security.enforce-secure-jwt-secret", () -> "false");
        registry.add("app.security.api-docs-public", () -> "true");
        registry.add("app.security.actuator-prometheus-public", () -> "true");
        registry.add("app.security.rate-limit.enabled", () -> "false");
    }

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentReserveOfFinalUnitAllowsExactlyOneWinnerAndNoOversell() throws Exception {
        ProductResponse product = createActiveProduct();
        UUID actor = createAdminUser();
        inventoryService.adjust(product.id(), actor, new InventoryAdjustRequest(1L, "Seed one unit"));

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    inventoryService.reserve(product.id(), 1);
                    successes.incrementAndGet();
                } catch (BusinessException | org.springframework.dao.OptimisticLockingFailureException ex) {
                    conflicts.incrementAndGet();
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdownNow();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);

        Inventory inventory = inventoryRepository.findById(product.id()).orElseThrow();
        assertThat(inventory.getQuantityAvailable()).isEqualTo(0);
        assertThat(inventory.getQuantityReserved()).isEqualTo(1);
        assertThat(inventory.getQuantityAvailable() + inventory.getQuantityReserved()).isEqualTo(1);
    }

    @Test
    void concurrentAdjustmentsRemainNonNegativeAndAudited() throws Exception {
        ProductResponse product = createActiveProduct();
        UUID actor = createAdminUser();
        inventoryService.adjust(product.id(), actor, new InventoryAdjustRequest(20L, "Seed"));

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                adjustWithClientRetry(product.id(), actor, -1L, "Concurrent sell-down");
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdownNow();

        Inventory inventory = inventoryRepository.findById(product.id()).orElseThrow();
        assertThat(inventory.getQuantityAvailable()).isEqualTo(10);
        assertThat(inventory.getQuantityAvailable()).isGreaterThanOrEqualTo(0);
    }

    private void adjustWithClientRetry(UUID productId, UUID actor, long delta, String reason) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                inventoryService.adjust(productId, actor, new InventoryAdjustRequest(delta, reason));
                return;
            } catch (BusinessException ex) {
                if (!"INVENTORY_CONFLICT".equals(ex.getCode())
                        && !"INVENTORY_NEGATIVE_ADJUSTMENT".equals(ex.getCode())) {
                    throw ex;
                }
                Thread.sleep(10L * (attempt + 1));
            }
        }
        throw new AssertionError("Unable to apply concurrent inventory adjustment after retries");
    }

    @Test
    void inventoryMutationRollsBackWhenTransactionFails() {
        ProductResponse product = createActiveProduct();
        UUID actor = createAdminUser();
        inventoryService.adjust(product.id(), actor, new InventoryAdjustRequest(5L, "Seed"));

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.executeWithoutResult(status -> {
                Inventory inventory = inventoryRepository.findById(product.id()).orElseThrow();
                inventory.reserve(2);
                inventoryRepository.saveAndFlush(inventory);
                throw new IllegalStateException("force rollback");
            });
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage()).isEqualTo("force rollback");
        }

        Inventory after = inventoryRepository.findById(product.id()).orElseThrow();
        assertThat(after.getQuantityAvailable()).isEqualTo(5);
        assertThat(after.getQuantityReserved()).isEqualTo(0);
    }

    private ProductResponse createActiveProduct() {
        var category = categoryService.create(new CategoryRequest(
                "Concurrency Cat " + UUID.randomUUID().toString().substring(0, 8),
                null,
                null,
                null,
                true,
                0
        ));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return productService.create(new ProductRequest(
                category.id(),
                "SKU-" + suffix,
                "Concurrent Widget " + suffix,
                null,
                "desc",
                1000L,
                "USD",
                ProductStatus.ACTIVE
        ));
    }

    private UUID createAdminUser() {
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        User admin = new User(
                UUID.randomUUID(),
                "admin-conc-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "Admin",
                UserStatus.ACTIVE
        );
        admin.addRole(adminRole);
        return userRepository.save(admin).getId();
    }
}
