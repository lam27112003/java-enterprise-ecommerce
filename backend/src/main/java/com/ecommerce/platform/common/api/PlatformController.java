package com.ecommerce.platform.common.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {

    private final String applicationName;
    private final String environment;

    public PlatformController(
            @Value("${spring.application.name:ecommerce-platform}") String applicationName,
            @Value("${app.environment:local}") String environment) {
        this.applicationName = applicationName;
        this.environment = environment;
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        return ApiResponse.ok(Map.of(
                "name", applicationName,
                "environment", environment,
                "phase", "1-foundation",
                "status", "UP"
        ));
    }
}
