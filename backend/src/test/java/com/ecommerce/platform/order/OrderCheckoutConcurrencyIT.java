package com.ecommerce.platform.order;

import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductResponse;
import com.ecommerce.platform.catalog.service.CategoryService;
import com.ecommerce.platform.catalog.service.ProductService;
import com.ecommerce.platform.cart.dto.CartDtos.AddCartItemRequest;
import com.ecommerce.platform.cart.service.CartService;
import com.ecommerce.platform.common.exception.BusinessException;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryAdjustRequest;
import com.ecommerce.platform.inventory.repository.InventoryRepository;
import com.ecommerce.platform.inventory.service.InventoryService;
import com.ecommerce.platform.order.dto.OrderDtos.CheckoutRequest;
import com.ecommerce.platform.order.dto.OrderDtos.OrderResponse;
import com.ecommerce.platform.order.service.OrderService;
import com.ecommerce.platform.promotion.domain.PromotionType;
import com.ecommerce.platform.promotion.dto.PromotionDtos.PromotionRequest;
import com.ecommerce.platform.promotion.repository.PromotionRepository;
import com.ecommerce.platform.promotion.service.PromotionService;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
class OrderCheckoutConcurrencyIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("OrderCheckoutConcurrencyIT");
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
        registry.add("app.security.jwt.secret", () -> "integration-checkout-pg-secret-must32b!");
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
    private CartService cartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PromotionService promotionService;
    @Autowired
    private PromotionRepository promotionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void concurrentCheckoutOfLastUnitAllowsExactlyOneOrder() throws Exception {
        ProductResponse product = createProduct();
        UUID admin = createAdmin();
        inventoryService.adjust(product.id(), admin, new InventoryAdjustRequest(1L, "one"));

        int threads = 8;
        List<UUID> customers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID userId = createCustomer();
            customers.add(userId);
            cartService.addItem(userId, new AddCartItemRequest(product.id(), 1));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (UUID userId : customers) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    orderService.checkout(userId, "k-" + userId, new CheckoutRequest(null, null));
                    successes.incrementAndGet();
                } catch (BusinessException | OptimisticLockingFailureException ex) {
                    failures.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdownNow();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(threads - 1);
        var inv = inventoryRepository.findById(product.id()).orElseThrow();
        assertThat(inv.getQuantityAvailable()).isZero();
        assertThat(inv.getQuantityReserved()).isEqualTo(1);
    }

    @Test
    void concurrentPromoUsageLimitAllowsExactlyLimitRedemptions() throws Exception {
        ProductResponse product = createProduct();
        UUID admin = createAdmin();
        inventoryService.adjust(product.id(), admin, new InventoryAdjustRequest(20L, "seed"));
        String code = "LIM1-" + UUID.randomUUID().toString().substring(0, 6);
        promotionService.create(new PromotionRequest(
                code, code, null, PromotionType.FIXED, 100L, null, null, 1, 0L, true));

        int threads = 6;
        List<UUID> customers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID userId = createCustomer();
            customers.add(userId);
            cartService.addItem(userId, new AddCartItemRequest(product.id(), 1));
        }

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (UUID userId : customers) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    OrderResponse order = orderService.checkout(
                            userId, "p-" + userId, new CheckoutRequest(code, null));
                    assertThat(order.promotionCode()).isEqualToIgnoringCase(code);
                    successes.incrementAndGet();
                } catch (RuntimeException ignored) {
                    // expected for losers
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdownNow();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(promotionRepository.findByCodeIgnoreCase(code).orElseThrow().getUsedCount()).isEqualTo(1);
    }

    private ProductResponse createProduct() {
        var category = categoryService.create(new CategoryRequest(
                "C-" + UUID.randomUUID().toString().substring(0, 8), null, null, null, true, 0));
        String s = UUID.randomUUID().toString().substring(0, 8);
        return productService.create(new ProductRequest(
                category.id(), "SKU-" + s, "P-" + s, null, null, 1000L, "USD", ProductStatus.ACTIVE));
    }

    private UUID createAdmin() {
        Role admin = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        User user = new User(UUID.randomUUID(), "a-" + UUID.randomUUID() + "@ex.com",
                passwordEncoder.encode("Str0ng!Pass"), "A", UserStatus.ACTIVE);
        user.addRole(admin);
        return userRepository.save(user).getId();
    }

    private UUID createCustomer() {
        Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
        User user = new User(UUID.randomUUID(), "c-" + UUID.randomUUID() + "@ex.com",
                passwordEncoder.encode("Str0ng!Pass"), "C", UserStatus.ACTIVE);
        user.addRole(customer);
        return userRepository.save(user).getId();
    }
}
