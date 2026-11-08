package com.ecommerce.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.inventory")
public class InventoryProperties {

    /** P7-D12: low-stock threshold for dashboard KPI (default 5). */
    private int lowStockThreshold = 5;

    public int getLowStockThreshold() {
        return lowStockThreshold;
    }

    public void setLowStockThreshold(int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }
}
