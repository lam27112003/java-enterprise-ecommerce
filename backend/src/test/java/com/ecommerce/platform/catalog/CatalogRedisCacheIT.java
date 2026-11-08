package com.ecommerce.platform.catalog;

import com.ecommerce.platform.catalog.cache.CatalogCacheService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
class CatalogRedisCacheIT {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("CatalogRedisCacheIT");
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:catalog_redis_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
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
        registry.add("app.security.jwt.secret", () -> "integration-catalog-redis-secret-32b-min");
        registry.add("app.catalog.cache.enabled", () -> "true");
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
    private CatalogCacheService catalogCacheService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void categoryTreeAndProductDetailAreCachedAndInvalidated() {
        var category = categoryService.create(new CategoryRequest(
                "Cached Cat", "cached-cat-" + UUID.randomUUID().toString().substring(0, 8), null, null, true, 0));
        categoryService.publicTree();
        assertThat(redisTemplate.hasKey("catalog:categories:tree")).isTrue();

        var product = productService.create(new ProductRequest(
                category.id(),
                "SKU-" + UUID.randomUUID().toString().substring(0, 8),
                "Cached Product",
                null,
                "desc",
                5000L,
                "USD",
                ProductStatus.ACTIVE
        ));
        productService.getPublicByIdOrSlug(product.slug());
        assertThat(redisTemplate.hasKey("catalog:product:slug:" + product.slug().toLowerCase())).isTrue();

        categoryService.update(category.id(), new CategoryRequest(
                "Cached Cat Updated", category.slug(), null, null, true, 0));
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey("catalog:categories:tree"))).isFalse();

        catalogCacheService.evictProduct(product.id(), product.slug());
        assertThat(Boolean.TRUE.equals(redisTemplate.hasKey("catalog:product:id:" + product.id()))).isFalse();
    }
}
