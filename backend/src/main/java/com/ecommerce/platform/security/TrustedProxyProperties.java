package com.ecommerce.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public class TrustedProxyProperties {

    /**
     * Comma-separated peer addresses (exact IP or CIDR) allowed to supply X-Forwarded-For / X-Real-IP.
     * Empty means forwarding headers are never trusted.
     */
    private List<String> trustedProxies = new ArrayList<>();

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? new ArrayList<>() : trustedProxies;
    }
}
