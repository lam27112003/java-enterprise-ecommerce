package com.ecommerce.platform.admin.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Phase 2 probe endpoint for ADMIN RBAC verification.
 * Broader admin features arrive in later phases.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminProbeController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.ok(Map.of(
                "scope", "ADMIN",
                "userId", SecurityUtils.currentUser().getId().toString(),
                "email", SecurityUtils.currentUser().getUsername()
        ));
    }
}
