package com.ecommerce.platform.user.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Phase 2 probe endpoint for CUSTOMER/ADMIN RBAC verification.
 */
@RestController
@RequestMapping("/api/v1/customer")
public class CustomerProbeController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.ok(Map.of(
                "scope", "CUSTOMER",
                "userId", SecurityUtils.currentUser().getId().toString(),
                "email", SecurityUtils.currentUser().getUsername()
        ));
    }
}
