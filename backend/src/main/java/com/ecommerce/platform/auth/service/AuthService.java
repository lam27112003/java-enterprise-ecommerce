package com.ecommerce.platform.auth.service;

import com.ecommerce.platform.auth.dto.AuthTokensResponse;
import com.ecommerce.platform.auth.dto.LoginRequest;
import com.ecommerce.platform.auth.dto.RegisterRequest;
import com.ecommerce.platform.auth.dto.UserProfileResponse;
import com.ecommerce.platform.auth.exception.AuthExceptions;
import com.ecommerce.platform.security.AuthenticatedUser;
import com.ecommerce.platform.security.DatabaseUserDetailsService;
import com.ecommerce.platform.security.JwtService;
import com.ecommerce.platform.security.ParsedAccessToken;
import com.ecommerce.platform.security.ratelimit.AuthRateLimiter;
import com.ecommerce.platform.security.token.AccessTokenDenylist;
import com.ecommerce.platform.user.domain.Role;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import com.ecommerce.platform.user.repository.RoleRepository;
import com.ecommerce.platform.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final DatabaseUserDetailsService userDetailsService;
    private final AccessTokenDenylist accessTokenDenylist;
    private final AuthRateLimiter authRateLimiter;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            DatabaseUserDetailsService userDetailsService,
            AccessTokenDenylist accessTokenDenylist,
            AuthRateLimiter authRateLimiter) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userDetailsService = userDetailsService;
        this.accessTokenDenylist = accessTokenDenylist;
        this.authRateLimiter = authRateLimiter;
    }

    @Transactional
    public AuthTokensResponse register(RegisterRequest request, String clientIp) {
        authRateLimiter.checkRegister(clientIp);
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw AuthExceptions.duplicateEmail();
        }

        Role customerRole = roleRepository.findByCode(RoleCode.CUSTOMER)
                .orElseThrow(() -> AuthExceptions.roleMissing(RoleCode.CUSTOMER.name()));

        User user = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                UserStatus.ACTIVE
        );
        user.addRole(customerRole);
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw AuthExceptions.duplicateEmail();
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthTokensResponse login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        authRateLimiter.checkLogin(clientIp, email);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(AuthExceptions::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw AuthExceptions.invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AuthExceptions.accountDisabled();
        }

        return issueTokens(user);
    }

    @Transactional
    public AuthTokensResponse refresh(String refreshToken, String clientIp) {
        authRateLimiter.checkRefresh(clientIp);
        RefreshTokenService.IssuedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
        User user = rotated.entity().getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenService.revokeAllForUser(user.getId());
            throw AuthExceptions.accountDisabled();
        }
        AuthenticatedUser principal = userDetailsService.toAuthenticatedUser(user);
        String accessToken = jwtService.createAccessToken(principal);
        return toResponse(user, accessToken, rotated.rawToken());
    }

    @Transactional
    public void logout(UUID userId, String accessToken, String refreshToken) {
        if (accessToken != null && !accessToken.isBlank()) {
            ParsedAccessToken parsed = jwtService.parseAndValidate(accessToken);
            if (!parsed.userId().equals(userId)) {
                throw AuthExceptions.invalidCredentials();
            }
            accessTokenDenylist.revoke(parsed.jti(), parsed.expiresAt());
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        } else {
            refreshTokenService.revokeAllForUser(userId);
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(AuthExceptions::invalidCredentials);
        return toProfile(user);
    }

    private AuthTokensResponse issueTokens(User user) {
        AuthenticatedUser principal = userDetailsService.toAuthenticatedUser(user);
        String accessToken = jwtService.createAccessToken(principal);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user);
        return toResponse(user, accessToken, refresh.rawToken());
    }

    private AuthTokensResponse toResponse(User user, String accessToken, String refreshToken) {
        return new AuthTokensResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                roleNames(user)
        );
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus().name(),
                roleNames(user),
                user.getCreatedAt()
        );
    }

    private List<String> roleNames(User user) {
        return user.getRoles().stream()
                .map(role -> role.getCode().name())
                .sorted()
                .toList();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
