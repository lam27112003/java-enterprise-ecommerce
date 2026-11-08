package com.ecommerce.platform.auth.service;

import com.ecommerce.platform.auth.domain.RefreshToken;
import com.ecommerce.platform.auth.exception.InvalidTokenException;
import com.ecommerce.platform.auth.repository.RefreshTokenRepository;
import com.ecommerce.platform.security.JwtProperties;
import com.ecommerce.platform.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public IssuedRefreshToken issue(User user) {
        return issue(user, UUID.randomUUID());
    }

    /**
     * Rotates a refresh token under a pessimistic row lock so only one concurrent
     * caller can succeed for a given raw token.
     */
    @Transactional
    public IssuedRefreshToken rotate(String rawRefreshToken) {
        Instant now = Instant.now();
        String hash = hash(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid"));

        if (existing.isRevoked()) {
            refreshTokenRepository.revokeFamily(existing.getFamilyId(), now);
            throw new InvalidTokenException("Refresh token reuse detected");
        }
        if (existing.isExpired(now)) {
            existing.revoke(now);
            refreshTokenRepository.save(existing);
            throw new InvalidTokenException("Refresh token has expired");
        }

        IssuedRefreshToken issued = issue(existing.getUser(), existing.getFamilyId());
        existing.markReplacedBy(issued.entity().getId(), now);
        refreshTokenRepository.save(existing);
        return issued;
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        Instant now = Instant.now();
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke(now);
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    private IssuedRefreshToken issue(User user, UUID familyId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getRefreshTokenTtlDays() * 24 * 60 * 60);
        String rawToken = generateRawToken();
        RefreshToken entity = new RefreshToken(
                UUID.randomUUID(),
                user,
                hash(rawToken),
                familyId,
                expiresAt
        );
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(rawToken, entity);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    public record IssuedRefreshToken(String rawToken, RefreshToken entity) {
    }
}
