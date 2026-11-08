package com.ecommerce.platform.inventory;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryCartIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminAdjustsStockWithAuditAndCustomerUsesCart() throws Exception {
        String adminToken = loginAsAdminOnly();
        String customerToken = registerAndLoginCustomer("cust-cart-" + UUID.randomUUID() + "@example.com");
        String otherToken = registerAndLoginCustomer("other-cart-" + UUID.randomUUID() + "@example.com");

        String categoryId = createCategory(adminToken);
        String productId = createProduct(adminToken, categoryId, "ACTIVE");

        mockMvc.perform(get("/api/v1/admin/inventory/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityAvailable").value(0))
                .andExpect(jsonPath("$.data.quantityReserved").value(0));

        mockMvc.perform(patch("/api/v1/admin/inventory/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "delta": 5, "reason": "Initial stock receipt" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delta").value(5))
                .andExpect(jsonPath("$.data.quantityAvailableAfter").value(5))
                .andExpect(jsonPath("$.data.reason").value("Initial stock receipt"));

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_adjustments WHERE product_id = ? AND delta = 5",
                Integer.class,
                UUID.fromString(productId));
        assertThat(auditCount).isEqualTo(1);

        mockMvc.perform(patch("/api/v1/admin/inventory/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "delta": -10, "reason": "Would go negative" }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": 2 }
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.subtotalCents").value(39998));

        // cart does not reserve
        mockMvc.perform(get("/api/v1/admin/inventory/" + productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityAvailable").value(5))
                .andExpect(jsonPath("$.data.quantityReserved").value(0));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": 10 }
                                """.formatted(productId)))
                .andExpect(status().isConflict());

        String draftId = createProduct(adminToken, categoryId, "DRAFT");
        mockMvc.perform(patch("/api/v1/admin/inventory/" + draftId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "delta": 3, "reason": "Draft stock" }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": 1 }
                                """.formatted(draftId)))
                .andExpect(status().isConflict());

        // ADMIN cannot use cart
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        // ownership: other customer cannot mutate first customer's items
        MvcResult cart = mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn();
        String itemId = objectMapper.readTree(cart.getResponse().getContentAsString())
                .path("data").path("items").get(0).path("id").asText();

        mockMvc.perform(patch("/api/v1/cart/items/" + itemId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantity": 1 }
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/cart")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void customerCannotAccessAdminInventory() throws Exception {
        String customerToken = registerAndLoginCustomer("cust-inv-" + UUID.randomUUID() + "@example.com");
        mockMvc.perform(get("/api/v1/admin/inventory")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    private String createCategory(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Inventory Cat",
                                  "slug": "inv-cat-%s",
                                  "active": true
                                }
                                """.formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String createProduct(String adminToken, String categoryId, String status) throws Exception {
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": "%s",
                                  "sku": "%s",
                                  "name": "Widget %s",
                                  "priceCents": 19999,
                                  "currency": "USD",
                                  "status": "%s"
                                }
                                """.formatted(categoryId, sku, sku, status)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return data.path("id").asText();
    }

    private String registerAndLoginCustomer(String email) throws Exception {
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
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String loginAsAdminOnly() throws Exception {
        String email = "admin-inv-" + UUID.randomUUID() + "@example.com";
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        User admin = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode("Str0ng!Pass"),
                "Admin Inventory",
                UserStatus.ACTIVE
        );
        admin.addRole(adminRole);
        userRepository.save(admin);

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "Str0ng!Pass" }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }
}
