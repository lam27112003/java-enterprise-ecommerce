package com.ecommerce.platform.auth.api;

import com.ecommerce.platform.auth.dto.AuthTokensResponse;
import com.ecommerce.platform.auth.dto.LoginRequest;
import com.ecommerce.platform.auth.dto.LogoutRequest;
import com.ecommerce.platform.auth.dto.RefreshRequest;
import com.ecommerce.platform.auth.dto.RegisterRequest;
import com.ecommerce.platform.auth.dto.UserProfileResponse;
import com.ecommerce.platform.auth.service.AuthService;
import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.common.web.ClientIpResolver;
import com.ecommerce.platform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthTokensResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                "Registration successful",
                authService.register(request, clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokensResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                "Login successful",
                authService.login(request, clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokensResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                "Token refreshed",
                authService.refresh(request.refreshToken(), clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest httpRequest) {
        String refreshToken = request == null ? null : request.refreshToken();
        String accessToken = extractBearer(httpRequest);
        authService.logout(SecurityUtils.currentUser().getId(), accessToken, refreshToken);
        return ApiResponse.okMessage("Logged out");
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        return ApiResponse.ok(authService.me(SecurityUtils.currentUser().getId()));
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7).trim();
    }
}
