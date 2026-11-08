package com.ecommerce.platform.catalog.cache;

import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryResponse;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cache-aside for catalog reads. Fail-open on Redis errors (serve from DB).
 */
@Service
public class CatalogCacheService {

    private static final Logger log = LoggerFactory.getLogger(CatalogCacheService.class);
    private static final String TREE_KEY = "catalog:categories:tree";
    private static final String PRODUCT_ID_PREFIX = "catalog:product:id:";
    private static final String PRODUCT_SLUG_PREFIX = "catalog:product:slug:";

    private final CatalogCacheProperties properties;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactory;
    private final ObjectProvider<StringRedisTemplate> stringRedisTemplate;

    public CatalogCacheService(
            CatalogCacheProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactory,
            ObjectProvider<StringRedisTemplate> stringRedisTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redisConnectionFactory = redisConnectionFactory;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public List<CategoryResponse> categoryTree(Supplier<List<CategoryResponse>> loader) {
        if (!cacheAvailable()) {
            return loader.get();
        }
        try {
            String cached = redis().opsForValue().get(TREE_KEY);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            }
            List<CategoryResponse> value = loader.get();
            redis().opsForValue().set(
                    TREE_KEY,
                    objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(properties.getCategoryTreeTtlSeconds()));
            return value;
        } catch (Exception ex) {
            log.warn("Catalog category cache unavailable; loading from database", ex);
            return loader.get();
        }
    }

    public ProductResponse productById(UUID id, Supplier<ProductResponse> loader) {
        return product(PRODUCT_ID_PREFIX + id, loader);
    }

    public ProductResponse productBySlug(String slug, Supplier<ProductResponse> loader) {
        return product(PRODUCT_SLUG_PREFIX + slug.toLowerCase(), loader);
    }

    public void evictCategories() {
        deleteQuietly(TREE_KEY);
    }

    public void evictProduct(UUID id, String slug) {
        deleteQuietly(PRODUCT_ID_PREFIX + id);
        if (slug != null) {
            deleteQuietly(PRODUCT_SLUG_PREFIX + slug.toLowerCase());
        }
    }

    private ProductResponse product(String key, Supplier<ProductResponse> loader) {
        if (!cacheAvailable()) {
            return loader.get();
        }
        try {
            String cached = redis().opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, ProductResponse.class);
            }
            ProductResponse value = loader.get();
            redis().opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(properties.getProductTtlSeconds()));
            // dual-key for slug lookups
            redis().opsForValue().set(
                    PRODUCT_ID_PREFIX + value.id(),
                    objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(properties.getProductTtlSeconds()));
            redis().opsForValue().set(
                    PRODUCT_SLUG_PREFIX + value.slug().toLowerCase(),
                    objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(properties.getProductTtlSeconds()));
            return value;
        } catch (Exception ex) {
            log.warn("Catalog product cache unavailable; loading from database", ex);
            return loader.get();
        }
    }

    private boolean cacheAvailable() {
        return properties.isEnabled()
                && redisConnectionFactory.getIfAvailable() != null
                && stringRedisTemplate.getIfAvailable() != null;
    }

    private StringRedisTemplate redis() {
        return Optional.ofNullable(stringRedisTemplate.getIfAvailable())
                .orElseThrow(() -> new IllegalStateException("Redis template unavailable"));
    }

    private void deleteQuietly(String key) {
        if (!cacheAvailable()) {
            return;
        }
        try {
            redis().delete(key);
        } catch (Exception ex) {
            log.warn("Failed to evict catalog cache key={}", key, ex);
        }
    }
}
