package com.ecommerce.platform.inventory;

import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductResponse;
import com.ecommerce.platform.catalog.service.CategoryService;
import com.ecommerce.platform.catalog.service.ProductService;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryAdjustRequest;
import com.ecommerce.platform.inventory.repository.InventoryAdjustmentRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
class InventoryPostgresIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("InventoryPostgresIT");
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
        registry.add("app.security.jwt.secret", () -> "integration-inventory2-pg-secret-must32");
        registry.add("app.catalog.cache.enabled", () -> "false");
        registry.add("app.security.enforce-secure-jwt-secret", () -> "false");
        registry.add("app.security.api-docs-public", () -> "true");
        registry.add("app.security.actuator-prometheus-public", () -> "true");
        registry.add("app.security.rate-limit.enabled", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private InventoryAdjustmentRepository adjustmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void flywayCreatesInventoryCartTablesAndProductCreateSeedsInventory() {
        Integer tables = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.tables
                         WHERE table_schema = 'public'
                           AND table_name IN ('inventory', 'inventory_adjustments', 'carts', 'cart_items')
                        """,
                Integer.class);
        assertThat(tables).isEqualTo(4);

        var category = categoryService.create(new CategoryRequest(
                "PG Inv Cat", null, null, null, true, 0));
        ProductResponse product = productService.create(new ProductRequest(
                category.id(),
                "SKU-PG-" + UUID.randomUUID().toString().substring(0, 8),
                "PG Product",
                null,
                null,
                500L,
                "USD",
                ProductStatus.ACTIVE
        ));

        Integer inv = jdbcTemplate.queryForObject(
                "SELECT quantity_available FROM inventory WHERE product_id = ?",
                Integer.class,
                product.id());
        assertThat(inv).isEqualTo(0);

        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        User admin = new User(
                UUID.randomUUID(),
                "admin-pg-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "Admin",
                UserStatus.ACTIVE
        );
        admin.addRole(adminRole);
        UUID actor = userRepository.save(admin).getId();

        inventoryService.adjust(product.id(), actor, new InventoryAdjustRequest(7L, "Receipt"));
        inventoryService.reserve(product.id(), 2);
        inventoryService.commit(product.id(), 1);
        inventoryService.release(product.id(), 1);

        assertThat(inventoryService.get(product.id()).quantityAvailable()).isEqualTo(6);
        assertThat(inventoryService.get(product.id()).quantityReserved()).isEqualTo(0);
        assertThat(adjustmentRepository.findByProductIdOrderByCreatedAtDesc(product.id())).hasSize(1);
    }
}
