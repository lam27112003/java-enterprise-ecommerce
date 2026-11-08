package com.ecommerce.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.actuator-prometheus-public=false",
        "app.security.actuator-prometheus-username=prom-scrape",
        "app.security.actuator-prometheus-password=prom-secret",
        "management.endpoints.web.exposure.include=health,info,prometheus,metrics",
        "management.endpoint.prometheus.access=unrestricted"
})
class ActuatorPrometheusBasicAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousPrometheusDeniedWhenPublicFlagFalse() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void basicAuthScrapeSucceedsWithoutPublicFlag() throws Exception {
        String token = Base64.getEncoder()
                .encodeToString("prom-scrape:prom-secret".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Basic " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_")));
    }

    @Test
    void wrongBasicAuthDenied() throws Exception {
        String token = Base64.getEncoder()
                .encodeToString("prom-scrape:wrong".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, "Basic " + token))
                .andExpect(status().isUnauthorized());
    }
}
