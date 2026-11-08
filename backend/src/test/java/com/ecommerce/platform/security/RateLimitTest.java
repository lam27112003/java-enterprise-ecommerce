package com.ecommerce.platform.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.rate-limit.enabled=true",
        "app.security.rate-limit.login-per-minute=2",
        "app.security.rate-limit.register-per-minute=2",
        "app.security.rate-limit.refresh-per-minute=2",
        "app.security.rate-limit.window-seconds=2"
})
class RateLimitTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerEndpointReturns429WhenLimitExceeded() throws Exception {
        String ip = uniqueIp();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .with(remoteAddr(ip))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("rl-reg-" + UUID.randomUUID() + "-" + i + "@example.com")))
                    .andExpect(status().isCreated());
        }
        expectTooManyRequests(post("/api/v1/auth/register")
                .with(remoteAddr(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("rl-reg-overflow-" + UUID.randomUUID() + "@example.com")));
    }

    @Test
    void loginEndpointReturns429WhenLimitExceeded() throws Exception {
        String email = "rl-login-" + UUID.randomUUID() + "@example.com";
        String setupIp = uniqueIp();
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(remoteAddr(setupIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());

        String loginIp = uniqueIp();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(remoteAddr(loginIp))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email)))
                    .andExpect(status().isOk());
        }
        expectTooManyRequests(post("/api/v1/auth/login")
                .with(remoteAddr(loginIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email)));
    }

    @Test
    void refreshEndpointReturns429WhenLimitExceeded() throws Exception {
        String email = "rl-refresh-" + UUID.randomUUID() + "@example.com";
        String setupIp = uniqueIp();
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .with(remoteAddr(setupIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(register.getResponse().getContentAsString()).path("data");
        String refresh = data.path("refreshToken").asText();

        String refreshIp = uniqueIp();
        // First refresh rotates successfully.
        MvcResult first = mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(remoteAddr(refreshIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refresh)))
                .andExpect(status().isOk())
                .andReturn();
        String nextRefresh = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        // Second refresh with successor (still counts against IP window).
        MvcResult second = mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(remoteAddr(refreshIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(nextRefresh)))
                .andExpect(status().isOk())
                .andReturn();
        String thirdRefresh = objectMapper.readTree(second.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        expectTooManyRequests(post("/api/v1/auth/refresh")
                .with(remoteAddr(refreshIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(thirdRefresh)));
    }

    @Test
    void rateLimitRecoversAfterConfiguredWindow() throws Exception {
        String ip = uniqueIp();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/register")
                            .with(remoteAddr(ip))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody("rl-window-" + UUID.randomUUID() + "-" + i + "@example.com")))
                    .andExpect(status().isCreated());
        }
        expectTooManyRequests(post("/api/v1/auth/register")
                .with(remoteAddr(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody("rl-window-blocked-" + UUID.randomUUID() + "@example.com")));

        Thread.sleep(2500);

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(remoteAddr(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("rl-window-recovered-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isCreated());
    }

    private void expectTooManyRequests(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            // Prefer X-Forwarded-For so each test gets a collision-free client key
            // (ClientIpResolver reads this before remoteAddr).
            request.addHeader("X-Forwarded-For", ip);
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String uniqueIp() {
        // ClientIpResolver uses the first X-Forwarded-For hop as the rate-limit key.
        // Use a UUID so concurrent/sequential tests never share a window bucket.
        return "test-client-" + UUID.randomUUID();
    }

    private static String registerBody(String email) {
        return """
                {
                  "email": "%s",
                  "password": "Str0ng!Pass",
                  "fullName": "Rate Limit"
                }
                """.formatted(email);
    }

    private static String loginBody(String email) {
        return """
                {
                  "email": "%s",
                  "password": "Str0ng!Pass"
                }
                """.formatted(email);
    }

    private static String refreshBody(String refreshToken) {
        return """
                { "refreshToken": "%s" }
                """.formatted(refreshToken);
    }
}
