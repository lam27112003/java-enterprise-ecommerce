package com.ecommerce.platform.auth.dto;

import java.util.List;
import java.util.UUID;

public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        UUID userId,
        String email,
        String fullName,
        List<String> roles
) {
}
