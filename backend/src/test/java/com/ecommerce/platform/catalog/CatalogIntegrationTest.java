package com.ecommerce.platform.catalog;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogIntegrationTest {

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

    @Test
    void adminCanManageCatalogAndPublicCanReadActiveProducts() throws Exception {
        String adminToken = loginAsAdmin();

        MvcResult categoryResult = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Electronics",
                                  "slug": "electronics-%s",
                                  "description": "Gadgets",
                                  "active": true
                                }
                                """.formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").exists())
                .andReturn();
        JsonNode category = objectMapper.readTree(categoryResult.getResponse().getContentAsString()).path("data");
        String categoryId = category.path("id").asText();
        String categorySlug = category.path("slug").asText();

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='%s')]".formatted(categoryId)).exists());

        mockMvc.perform(get("/api/v1/categories/" + categorySlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Electronics"));

        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult productResult = mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": "%s",
                                  "sku": "%s",
                                  "name": "Noise Cancelling Headphones",
                                  "description": "Wireless",
                                  "priceCents": 19999,
                                  "currency": "USD",
                                  "status": "ACTIVE"
                                }
                                """.formatted(categoryId, sku)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sku").value(sku))
                .andReturn();
        JsonNode product = objectMapper.readTree(productResult.getResponse().getContentAsString()).path("data");
        String productId = product.path("id").asText();
        String productSlug = product.path("slug").asText();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hero.png",
                "image/png",
                "fake-png-bytes".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/products/" + productId + "/images")
                        .file(file)
                        .param("primary", "true")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.primary").value(true));

        mockMvc.perform(get("/api/v1/products")
                        .param("q", "Headphones")
                        .param("categoryId", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku").value(sku));

        mockMvc.perform(get("/api/v1/products/" + productSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images[0].primary").value(true));

        mockMvc.perform(put("/api/v1/admin/products/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": "%s",
                                  "sku": "%s",
                                  "name": "Noise Cancelling Headphones",
                                  "priceCents": 18999,
                                  "currency": "USD",
                                  "status": "DRAFT"
                                }
                                """.formatted(categoryId, sku)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/products/" + productSlug))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Blocked", "slug": "blocked-%s" }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotAccessAdminCatalog() throws Exception {
        String email = "customer-catalog-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Str0ng!Pass",
                                  "fullName": "Customer"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "Str0ng!Pass" }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private String loginAsAdmin() throws Exception {
        String email = "admin-catalog-" + UUID.randomUUID() + "@example.com";
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        Role customerRole = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
        User admin = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode("Str0ng!Pass"),
                "Admin Catalog",
                UserStatus.ACTIVE
        );
        admin.addRole(adminRole);
        admin.addRole(customerRole);
        userRepository.save(admin);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "Str0ng!Pass" }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
