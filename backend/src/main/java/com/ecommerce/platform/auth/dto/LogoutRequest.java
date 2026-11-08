package com.ecommerce.platform.auth.dto;

public record LogoutRequest(
        String refreshToken
) {
}
