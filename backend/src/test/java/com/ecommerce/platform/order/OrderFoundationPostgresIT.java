package com.ecommerce.platform.order;

import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductResponse;
import com.ecommerce.platform.catalog.service.CategoryService;
import com.ecommerce.platform.catalog.service.ProductService;
import com.ecommerce.platform.order.domain.CheckoutIdempotencyKey;
import com.ecommerce.platform.order.domain.Order;
import com.ecommerce.platform.order.domain.OrderItem;
import com.ecommerce.platform.order.domain.OrderStatus;
import com.ecommerce.platform.order.repository.CheckoutIdempotencyKeyRepository;
import com.ecommerce.platform.order.repository.OrderRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
class OrderFoundationPostgresIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("OrderFoundationPostgresIT");
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
        registry.add("app.security.jwt.secret", () -> "integration-orders-pg-secret-must-be-32b");
        registry.add("app.catalog.cache.enabled", () -> "false");
        registry.add("app.security.enforce-secure-jwt-secret", () -> "false");
        registry.add("app.security.api-docs-public", () -> "true");
        registry.add("app.security.actuator-prometheus-public", () -> "true");
        registry.add("app.security.rate-limit.enabled", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CheckoutIdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void flywayCreatesOrderTablesAndPersistsImmutableLineSnapshots() {
        Integer tables = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.tables
                         WHERE table_schema = 'public'
                           AND table_name IN ('orders', 'order_items', 'checkout_idempotency_keys')
                        """,
                Integer.class);
        assertThat(tables).isEqualTo(3);

        String taxDefaultStr = jdbcTemplate.queryForObject(
                """
                        SELECT column_default::text
                          FROM information_schema.columns
                         WHERE table_schema = 'public'
                           AND table_name = 'orders'
                           AND column_name = 'tax_cents'
                        """,
                String.class);
        assertThat(taxDefaultStr).contains("0");

        UUID userId = createCustomer();
        ProductResponse product = createProduct();

        Order order = new Order(
                UUID.randomUUID(),
                "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                userId,
                OrderStatus.PENDING_PAYMENT,
                product.priceCents() * 2,
                0L,
                0L,
                product.priceCents() * 2,
                "USD"
        );
        order.addItem(new OrderItem(
                UUID.randomUUID(),
                order,
                product.id(),
                product.name(),
                product.sku(),
                product.priceCents(),
                2
        ));
        orderRepository.saveAndFlush(order);

        Order loaded = orderRepository.findWithItemsById(order.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(loaded.getTaxCents()).isZero();
        assertThat(loaded.getItems()).hasSize(1);
        OrderItem line = loaded.getItems().getFirst();
        assertThat(line.getProductName()).isEqualTo(product.name());
        assertThat(line.getSku()).isEqualTo(product.sku());
        assertThat(line.getUnitPriceCents()).isEqualTo(product.priceCents());
        assertThat(line.getQuantity()).isEqualTo(2);
        assertThat(line.getLineTotalCents()).isEqualTo(product.priceCents() * 2);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT unit_price_cents, quantity, line_total_cents FROM order_items WHERE order_id = ?",
                order.getId());
        assertThat(((Number) row.get("line_total_cents")).longValue())
                .isEqualTo(((Number) row.get("unit_price_cents")).longValue()
                        * ((Number) row.get("quantity")).longValue());

        CheckoutIdempotencyKey key = new CheckoutIdempotencyKey(
                UUID.randomUUID(),
                userId,
                "key-" + UUID.randomUUID(),
                "hash-abc",
                order.getId()
        );
        idempotencyKeyRepository.saveAndFlush(key);

        assertThatThrownBy(() -> idempotencyKeyRepository.saveAndFlush(new CheckoutIdempotencyKey(
                UUID.randomUUID(),
                userId,
                key.getIdempotencyKey(),
                "hash-other",
                order.getId()
        ))).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO order_items
                        (id, order_id, product_id, product_name, sku, unit_price_cents, quantity, line_total_cents)
                        VALUES (?, ?, ?, 'Bad', 'SKU-X', 100, 2, 150)
                        """,
                UUID.randomUUID(), order.getId(), product.id()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID createCustomer() {
        Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
        User user = new User(
                UUID.randomUUID(),
                "order-cust-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "Order Customer",
                UserStatus.ACTIVE
        );
        user.addRole(customer);
        return userRepository.save(user).getId();
    }

    private ProductResponse createProduct() {
        var category = categoryService.create(new CategoryRequest(
                "Order Cat " + UUID.randomUUID().toString().substring(0, 8),
                null, null, null, true, 0));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return productService.create(new ProductRequest(
                category.id(),
                "SKU-O-" + suffix,
                "Order Product " + suffix,
                null,
                null,
                2500L,
                "USD",
                ProductStatus.ACTIVE
        ));
    }
}
