package com.ecommerce.platform.user.dto;

import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record AdminUserResponse(
            UUID id,
            String email,
            String fullName,
            UserStatus status,
            Set<RoleCode> roles,
            Instant createdAt) {
    }

    public record AdminUserStatusUpdateRequest(@NotNull UserStatus status) {
    }
}
