package com.ecommerce.platform.catalog;

import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductRequest;
import com.ecommerce.platform.catalog.service.CategoryService;
import com.ecommerce.platform.catalog.service.ProductService;
import com.ecommerce.platform.support.DockerInfrastructure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
class CatalogPostgresIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("CatalogPostgresIT");
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
        registry.add("app.security.jwt.secret", () -> "integration-catalog-pg-secret-must-be-32b");
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

    @Test
    void flywayCreatesCatalogTablesAndSupportsSearch() {
        Integer tables = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.tables
                         WHERE table_schema = 'public'
                           AND table_name IN ('categories', 'products', 'product_images')
                        """,
                Integer.class);
        assertThat(tables).isEqualTo(3);

        var category = categoryService.create(new CategoryRequest(
                "Outdoor", "outdoor-" + UUID.randomUUID().toString().substring(0, 8), "Gear", null, true, 1));
        var product = productService.create(new ProductRequest(
                category.id(),
                "SKU-" + UUID.randomUUID().toString().substring(0, 8),
                "Trail Tent",
                null,
                "2-person tent",
                12999L,
                "USD",
                ProductStatus.ACTIVE
        ));

        var page = productService.searchPublic("Tent", category.id(), 1000L, 20000L, 0, 10, "priceCents", "asc");
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().id()).isEqualTo(product.id());
    }
}
