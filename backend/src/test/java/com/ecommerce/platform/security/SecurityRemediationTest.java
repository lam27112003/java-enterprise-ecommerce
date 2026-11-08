package com.ecommerce.platform.security;

import com.ecommerce.platform.auth.repository.RefreshTokenRepository;
import com.ecommerce.platform.auth.service.RefreshTokenService;
import com.ecommerce.platform.user.domain.Role;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import com.ecommerce.platform.user.repository.RoleRepository;
import com.ecommerce.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRemediationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void logoutRevokesAccessToken() throws Exception {
        JsonNode tokens = register("logout-" + UUID.randomUUID() + "@example.com");
        String access = tokens.path("accessToken").asText();
        String refresh = tokens.path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s" }
                                """.formatted(refresh)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s" }
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserJwtIsRejected() throws Exception {
        String email = "disabled-" + UUID.randomUUID() + "@example.com";
        JsonNode tokens = register(email);
        String access = tokens.path("accessToken").asText();

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void suspendedUserJwtIsRejected() throws Exception {
        String email = "suspended-" + UUID.randomUUID() + "@example.com";
        JsonNode tokens = register(email);
        String access = tokens.path("accessToken").asText();

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void demotedAdminLosesAdminAccessImmediately() throws Exception {
        String email = "demote-" + UUID.randomUUID() + "@example.com";
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();

        User admin = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode("Str0ng!Pass"),
                "Admin",
                UserStatus.ACTIVE
        );
        admin.addRole(adminRole);
        admin.addRole(customerRole);
        userRepository.save(admin);

        AuthenticatedUser principal = new AuthenticatedUser(
                admin.getId(),
                email,
                admin.getPasswordHash(),
                "Admin",
                UserStatus.ACTIVE,
                Set.of(RoleCode.ADMIN, RoleCode.CUSTOMER)
        );
        String access = jwtService.createAccessToken(principal);

        mockMvc.perform(get("/api/v1/admin/ping")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        User persisted = userRepository.findById(admin.getId()).orElseThrow();
        persisted.getRoles().removeIf(role -> role.getCode() == RoleCode.ADMIN);
        userRepository.saveAndFlush(persisted);

        mockMvc.perform(get("/api/v1/admin/ping")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/customer/ping")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void deletedUserJwtIsRejected() throws Exception {
        String email = "deleted-" + UUID.randomUUID() + "@example.com";
        JsonNode tokens = register(email);
        String access = tokens.path("accessToken").asText();
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        UUID userId = user.getId();

        transactionTemplate.executeWithoutResult(status -> {
            refreshTokenRepository.deleteAll(
                    refreshTokenRepository.findAll().stream()
                            .filter(rt -> rt.getUser().getId().equals(userId))
                            .toList());
            userRepository.deleteById(userId);
        });

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void concurrentRefreshAllowsOnlyOneSuccess() throws Exception {
        JsonNode tokens = register("concurrent-" + UUID.randomUUID() + "@example.com");
        String refresh = tokens.path("refreshToken").asText();
        String hash = RefreshTokenService.hash(refresh);
        UUID familyId = refreshTokenRepository.findByTokenHash(hash).orElseThrow().getFamilyId();

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            { "refreshToken": "%s" }
                                            """.formatted(refresh)))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        success.incrementAndGet();
                    } else {
                        failure.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failure.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = pool.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(threads - 1);
        assertThat(refreshTokenRepository.countByFamilyIdAndRevokedAtIsNull(familyId)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s" }
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Str0ng!Pass",
                                  "fullName": "Remediation User"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
