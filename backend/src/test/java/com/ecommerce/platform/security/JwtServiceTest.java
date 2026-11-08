package com.ecommerce.platform.security;

import com.ecommerce.platform.auth.exception.InvalidTokenException;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-must-be-at-least-32-chars");
        properties.setIssuer("unit-test-issuer");
        properties.setAccessTokenTtlMinutes(15);
        jwtService = new JwtService(properties);
        jwtService.init();
    }

    @Test
    void createsAndParsesAccessTokenWithJti() {
        AuthenticatedUser user = sampleUser();
        String token = jwtService.createAccessToken(user);

        ParsedAccessToken parsed = jwtService.parseAndValidate(token);

        assertThat(parsed.userId()).isEqualTo(user.getId());
        assertThat(parsed.email()).isEqualTo(user.getUsername());
        assertThat(parsed.jti()).isNotBlank();
        assertThat(parsed.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.createAccessToken(sampleUser());
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsExpiredToken() throws InterruptedException {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-must-be-at-least-32-chars");
        properties.setIssuer("unit-test-issuer");
        properties.setAccessTokenTtlMinutes(0);
        JwtService shortLived = new JwtService(properties);
        shortLived.init();

        String token = shortLived.createAccessToken(sampleUser());
        Thread.sleep(1100);

        assertThatThrownBy(() -> shortLived.parseAndValidate(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    private AuthenticatedUser sampleUser() {
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "customer@example.com",
                "hash",
                "Customer",
                UserStatus.ACTIVE,
                Set.of(RoleCode.CUSTOMER)
        );
    }
}
