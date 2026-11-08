package com.ecommerce.platform.catalog.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.catalog.cache")
public class CatalogCacheProperties {

    private boolean enabled = true;
    private long categoryTreeTtlSeconds = 300;
    private long productTtlSeconds = 180;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCategoryTreeTtlSeconds() {
        return categoryTreeTtlSeconds;
    }

    public void setCategoryTreeTtlSeconds(long categoryTreeTtlSeconds) {
        this.categoryTreeTtlSeconds = categoryTreeTtlSeconds;
    }

    public long getProductTtlSeconds() {
        return productTtlSeconds;
    }

    public void setProductTtlSeconds(long productTtlSeconds) {
        this.productTtlSeconds = productTtlSeconds;
    }
}
