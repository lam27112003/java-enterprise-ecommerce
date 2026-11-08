package com.ecommerce.platform.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Negative configuration checks for production-like JWT secret enforcement.
 * Actuator exposure is covered by {@link ActuatorExposureTest}.
 */
class ProductionSecurityConfigTest {

    @Test
    void missingProductionJwtSecretPreventsStartup() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("   ");
        JwtSecretsValidator validator = new JwtSecretsValidator(properties, "prod", true);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be set");
    }

    @Test
    void knownDevDefaultJwtSecretRejectedInProduction() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("local-dev-only-change-me-to-a-long-random-secret-key-32b");
        JwtSecretsValidator validator = new JwtSecretsValidator(properties, "prod", true);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("known development default");
    }

    @Test
    void dockerProfileRejectsForbiddenDefaultEvenWithoutExplicitFlag() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("standalone-dev-only-change-me-long-random-secret-key32");
        JwtSecretsValidator validator = new JwtSecretsValidator(properties, "docker", false);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("known development default");
    }

    @Test
    void shortJwtSecretRejectedByJwtService() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short-secret");
        JwtService jwtService = new JwtService(properties);

        assertThatThrownBy(jwtService::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
