package com.ecommerce.platform.user.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.security.SecurityUtils;
import com.ecommerce.platform.user.dto.AdminUserDtos.AdminUserResponse;
import com.ecommerce.platform.user.dto.AdminUserDtos.AdminUserStatusUpdateRequest;
import com.ecommerce.platform.user.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminUserService.list(q, page, size));
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminUserResponse> updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminUserStatusUpdateRequest request) {
        return ApiResponse.ok(adminUserService.updateStatus(
                userId,
                SecurityUtils.currentUser().getId(),
                request));
    }
}
