package com.ecommerce.platform.config;

import com.ecommerce.platform.catalog.cache.CatalogCacheProperties;
import com.ecommerce.platform.catalog.storage.CatalogStorageProperties;
import com.ecommerce.platform.security.JwtProperties;
import com.ecommerce.platform.security.TrustedProxyProperties;
import com.ecommerce.platform.security.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        RateLimitProperties.class,
        TrustedProxyProperties.class,
        CatalogStorageProperties.class,
        CatalogCacheProperties.class,
        StripeProperties.class,
        InventoryProperties.class
})
public class AppPropertiesConfig {
}
