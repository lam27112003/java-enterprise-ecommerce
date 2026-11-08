package com.ecommerce.platform.order;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderCheckoutIntegrationTest {

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
    void checkoutIdempotencyCancelRestoreAndAdminPaymentFailed() throws Exception {
        String adminToken = loginAsAdminOnly();
        String customerToken = registerCustomer("c-" + UUID.randomUUID() + "@example.com");
        String otherToken = registerCustomer("o-" + UUID.randomUUID() + "@example.com");

        String categoryId = createCategory(adminToken);
        String productId = createProduct(adminToken, categoryId, 2000L);
        seedStock(adminToken, productId, 5);
        createPromotion(adminToken, "SAVE10", "PERCENT", 10, 1);

        addToCart(customerToken, productId, 2);

        String idem = "idem-" + UUID.randomUUID();
        MvcResult first = mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "promotionCode": "SAVE10", "shippingAddressJson": "{\\"city\\":\\"HN\\"}" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.taxCents").value(0))
                .andExpect(jsonPath("$.data.discountCents").value(400))
                .andExpect(jsonPath("$.data.totalCents").value(3600))
                .andExpect(jsonPath("$.data.items[0].unitPriceCents").value(2000))
                .andReturn();
        String orderId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "promotionCode": "SAVE10", "shippingAddressJson": "{\\"city\\":\\"HN\\"}" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(orderId));

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "promotionCode": "SAVE10", "shippingAddressJson": "{\\"city\\":\\"DN\\"}" }
                                """))
                .andExpect(status().isConflict());

        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT quantity_reserved FROM inventory WHERE product_id = ?",
                Integer.class,
                UUID.fromString(productId));
        assertThat(reserved).isEqualTo(2);
        Integer used = jdbcTemplate.queryForObject(
                "SELECT used_count FROM promotions WHERE code = 'SAVE10'", Integer.class);
        assertThat(used).isEqualTo(1);

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT quantity_reserved FROM inventory WHERE product_id = ?",
                Integer.class, UUID.fromString(productId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT used_count FROM promotions WHERE code = 'SAVE10'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT inventory_released FROM orders WHERE id = ?",
                Boolean.class, UUID.fromString(orderId))).isTrue();

        // second order + admin PAYMENT_FAILED
        seedStock(adminToken, productId, 3);
        addToCart(customerToken, productId, 1);
        createPromotion(adminToken, "FLAT100", "FIXED", 100, 5);
        MvcResult second = mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "idem2-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "promotionCode": "FLAT100" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String order2 = objectMapper.readTree(second.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(patch("/api/v1/admin/orders/" + order2 + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PAID" }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/admin/orders/" + order2 + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PAYMENT_FAILED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAYMENT_FAILED"));

        mockMvc.perform(patch("/api/v1/admin/orders/" + order2 + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PAYMENT_FAILED" }
                                """))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT used_count FROM promotions WHERE code = 'FLAT100'", Integer.class)).isZero();
    }

    private void seedStock(String adminToken, String productId, long delta) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/inventory/" + productId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "delta": %d, "reason": "seed" }
                                """.formatted(delta)))
                .andExpect(status().isOk());
    }

    private void addToCart(String token, String productId, int qty) throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": %d }
                                """.formatted(productId, qty)))
                .andExpect(status().isOk());
    }

    private void createPromotion(String adminToken, String code, String type, long value, int limit) throws Exception {
        mockMvc.perform(post("/api/v1/admin/promotions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "type": "%s",
                                  "valueCentsOrPercent": %d,
                                  "usageLimit": %d,
                                  "minOrderAmountCents": 0,
                                  "active": true
                                }
                                """.formatted(code, code, type, value, limit)))
                .andExpect(status().isCreated());
    }

    private String createCategory(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "OrdCat", "slug": "ord-%s", "active": true }
                                """.formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String createProduct(String adminToken, String categoryId, long price) throws Exception {
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId": "%s",
                                  "sku": "%s",
                                  "name": "Prod %s",
                                  "priceCents": %d,
                                  "currency": "USD",
                                  "status": "ACTIVE"
                                }
                                """.formatted(categoryId, sku, sku, price)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String registerCustomer(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "Str0ng!Pass", "fullName": "Customer" }
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
        String email = "admin-o-" + UUID.randomUUID() + "@example.com";
        Role adminRole = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        User admin = new User(
                UUID.randomUUID(), email, passwordEncoder.encode("Str0ng!Pass"), "Admin", UserStatus.ACTIVE);
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
