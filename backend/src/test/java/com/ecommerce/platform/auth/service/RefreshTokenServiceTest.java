package com.ecommerce.platform.auth.service;

import com.ecommerce.platform.auth.domain.RefreshToken;
import com.ecommerce.platform.auth.exception.InvalidTokenException;
import com.ecommerce.platform.auth.repository.RefreshTokenRepository;
import com.ecommerce.platform.security.JwtProperties;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;
    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshTokenTtlDays(14);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, properties);
        user = new User(UUID.randomUUID(), "user@example.com", "hash", "User", UserStatus.ACTIVE);
    }

    @Test
    void issueStoresHashedToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken issued = refreshTokenService.issue(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(issued.rawToken()));
        assertThat(captor.getValue().getTokenHash()).isNotEqualTo(issued.rawToken());
    }

    @Test
    void rotateRevokesPreviousToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RefreshTokenService.IssuedRefreshToken first = refreshTokenService.issue(user);
        RefreshToken stored = first.entity();
        when(refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenService.hash(first.rawToken())))
                .thenReturn(Optional.of(stored));

        RefreshTokenService.IssuedRefreshToken second = refreshTokenService.rotate(first.rawToken());

        assertThat(stored.isRevoked()).isTrue();
        assertThat(stored.getReplacedByTokenId()).isEqualTo(second.entity().getId());
        assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
    }

    @Test
    void reuseOfRevokedTokenRevokesFamily() {
        String raw = "reuse-token-value";
        UUID familyId = UUID.randomUUID();
        RefreshToken revoked = new RefreshToken(
                UUID.randomUUID(),
                user,
                RefreshTokenService.hash(raw),
                familyId,
                Instant.now().plusSeconds(60)
        );
        revoked.revoke(Instant.now());
        when(refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenService.hash(raw)))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate(raw))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("reuse");
        verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    }
}
