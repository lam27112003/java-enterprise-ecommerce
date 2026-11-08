package com.ecommerce.platform.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Fails fast when production (or docker) runs with missing/known-insecure JWT secrets.
 */
@Component
public class JwtSecretsValidator {

    private static final Set<String> FORBIDDEN_DEFAULTS = Set.of(
            "phase1-dev-only-change-me-to-a-long-random-secret-key-32bytes",
            "local-dev-only-change-me-to-a-long-random-secret-key-32b",
            "standalone-dev-only-change-me-long-random-secret-key32",
            "test-secret-key-must-be-at-least-32-characters-long"
    );

    private final JwtProperties jwtProperties;
    private final String environment;
    private final boolean enforceSecureSecret;

    public JwtSecretsValidator(
            JwtProperties jwtProperties,
            @Value("${app.environment:local}") String environment,
            @Value("${app.security.enforce-secure-jwt-secret:false}") boolean enforceSecureSecret) {
        this.jwtProperties = jwtProperties;
        this.environment = environment;
        this.enforceSecureSecret = enforceSecureSecret;
    }

    @PostConstruct
    void validate() {
        String secret = jwtProperties.getSecret();
        boolean productionLike = enforceSecureSecret
                || "prod".equalsIgnoreCase(environment)
                || "docker".equalsIgnoreCase(environment);

        if (!productionLike) {
            return;
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be set for environment=" + environment);
        }
        String normalized = secret.trim().toLowerCase(Locale.ROOT);
        if (FORBIDDEN_DEFAULTS.stream().map(String::toLowerCase).anyMatch(normalized::equals)) {
            throw new IllegalStateException(
                    "JWT_SECRET must not use a known development default in environment=" + environment);
        }
    }
}
