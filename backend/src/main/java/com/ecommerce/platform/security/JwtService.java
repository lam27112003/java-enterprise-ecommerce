package com.ecommerce.platform.security;

import com.ecommerce.platform.auth.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private SecretKey secretKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be configured via JWT_SECRET and be at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getAccessTokenTtlMinutes() * 60);
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.getIssuer())
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim("email", user.getUsername())
                // roles claim retained for observability only — authorization uses DB roles
                .claim("roles", roles)
                .signWith(secretKey)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtlMinutes() * 60;
    }

    /**
     * Validates signature, issuer, and expiry. Does not authorize roles from claims.
     */
    public ParsedAccessToken parseAndValidate(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                throw new InvalidTokenException("Access token is missing jti");
            }
            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) {
                throw new InvalidTokenException("Access token claims are incomplete");
            }
            Date exp = claims.getExpiration();
            if (exp == null) {
                throw new InvalidTokenException("Access token is missing expiration");
            }
            return new ParsedAccessToken(jti, userId, email, exp.toInstant());
        } catch (ExpiredJwtException ex) {
            throw new InvalidTokenException("Access token has expired");
        } catch (IllegalArgumentException | JwtException ex) {
            throw new InvalidTokenException("Access token is invalid");
        }
    }
}
