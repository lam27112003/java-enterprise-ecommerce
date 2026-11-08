package com.ecommerce.platform.auth.service;

import com.ecommerce.platform.auth.domain.RefreshToken;
import com.ecommerce.platform.auth.dto.LoginRequest;
import com.ecommerce.platform.auth.dto.RegisterRequest;
import com.ecommerce.platform.common.exception.BusinessException;
import com.ecommerce.platform.security.AuthenticatedUser;
import com.ecommerce.platform.security.DatabaseUserDetailsService;
import com.ecommerce.platform.security.JwtService;
import com.ecommerce.platform.security.ratelimit.AuthRateLimiter;
import com.ecommerce.platform.security.token.AccessTokenDenylist;
import com.ecommerce.platform.user.domain.Role;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import com.ecommerce.platform.user.repository.RoleRepository;
import com.ecommerce.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private DatabaseUserDetailsService userDetailsService;
    @Mock
    private AccessTokenDenylist accessTokenDenylist;
    @Mock
    private AuthRateLimiter authRateLimiter;

    @InjectMocks
    private AuthService authService;

    private Role customerRole;

    @BeforeEach
    void setUp() {
        customerRole = new Role(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                RoleCode.CUSTOMER,
                "Storefront customer",
                Instant.now()
        );
        lenient().doNothing().when(authRateLimiter).checkRegister(anyString());
        lenient().doNothing().when(authRateLimiter).checkLogin(anyString(), anyString());
        lenient().doNothing().when(authRateLimiter).checkRefresh(anyString());
    }

    @Test
    void registerAssignsCustomerRoleOnly() {
        RegisterRequest request = new RegisterRequest("new@example.com", "Str0ng!Pass", "New User");
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByCode(RoleCode.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userDetailsService.toAuthenticatedUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new AuthenticatedUser(
                    user.getId(),
                    user.getEmail(),
                    user.getPasswordHash(),
                    user.getFullName(),
                    user.getStatus(),
                    user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet())
            );
        });
        when(jwtService.createAccessToken(any())).thenReturn("access");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new RefreshTokenService.IssuedRefreshToken(
                    "refresh",
                    new RefreshToken(UUID.randomUUID(), user, "hash", UUID.randomUUID(), Instant.now().plusSeconds(60))
            );
        });

        var response = authService.register(request, "127.0.0.1");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getRoles()).extracting(Role::getCode).containsExactly(RoleCode.CUSTOMER);
        assertThat(saved.hasRole(RoleCode.ADMIN)).isFalse();
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.roles()).containsExactly("CUSTOMER");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("dup@example.com", "Str0ng!Pass", "Dup"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void loginRejectsBadPassword() {
        User user = new User(UUID.randomUUID(), "user@example.com", "hash", "User", UserStatus.ACTIVE);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong"), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("INVALID_CREDENTIALS");
    }
}
