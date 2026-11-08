package com.ecommerce.platform.payment;

import com.ecommerce.platform.payment.provider.FakePaymentProvider;
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
class PaymentIntegrationTest {

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
    @Autowired
    private FakePaymentProvider fakePaymentProvider;

    @Test
    void payCreatesIntentAndDuplicateReturnsSameActiveIntent() throws Exception {
        Fixture fixture = checkoutFixture(1_500L, 1);
        JsonNode first = pay(fixture.customerToken(), fixture.orderId());
        assertThat(first.path("status").asText()).isEqualTo("REQUIRES_ACTION");
        assertThat(first.path("amountCents").asLong()).isEqualTo(1_500L);
        assertThat(first.path("currency").asText()).isEqualTo("USD");
        assertThat(first.path("clientSecret").asText()).startsWith("cs_test_");
        assertThat(first.path("providerRef").asText()).startsWith("pi_");

        JsonNode second = pay(fixture.customerToken(), fixture.orderId());
        assertThat(second.path("id").asText()).isEqualTo(first.path("id").asText());
        assertThat(second.path("providerRef").asText()).isEqualTo(first.path("providerRef").asText());
        assertThat(second.path("clientSecret").asText()).isEqualTo(first.path("clientSecret").asText());

        Integer payments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                Integer.class,
                UUID.fromString(fixture.orderId()));
        assertThat(payments).isEqualTo(1);
        String idempotencyKey = jdbcTemplate.queryForObject(
                "SELECT idempotency_key FROM payments WHERE id = ?",
                String.class,
                UUID.fromString(first.path("id").asText()));
        assertThat(idempotencyKey).isEqualTo("pay:" + fixture.orderId());
        Integer secretColumns = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE LOWER(table_name) = 'payments' AND LOWER(column_name) = 'client_secret'
                """,
                Integer.class);
        assertThat(secretColumns).isZero();
    }

    @Test
    void payRejectsPaymentFailedOrderAndOtherCustomers() throws Exception {
        Fixture fixture = checkoutFixture(2_000L, 1);
        String other = registerCustomer("other-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/api/v1/orders/" + fixture.orderId() + "/pay")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/admin/orders/" + fixture.orderId() + "/status")
                        .header("Authorization", "Bearer " + fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PAYMENT_FAILED" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/" + fixture.orderId() + "/pay")
                        .header("Authorization", "Bearer " + fixture.customerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));

        mockMvc.perform(patch("/api/v1/admin/orders/" + fixture.orderId() + "/status")
                        .header("Authorization", "Bearer " + fixture.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PAID" }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidSignatureDoesNotPayAndSuccessCommitsOnce() throws Exception {
        Fixture fixture = checkoutFixture(3_200L, 2);
        JsonNode payment = pay(fixture.customerToken(), fixture.orderId());
        String providerRef = payment.path("providerRef").asText();

        mockMvc.perform(get("/api/v1/orders/" + fixture.orderId() + "/payment")
                        .header("Authorization", "Bearer " + fixture.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerRef").value(providerRef))
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());

        mockMvc.perform(get("/api/v1/payments/" + payment.path("id").asText())
                        .header("Authorization", "Bearer " + registerCustomer("nope-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/payments/" + payment.path("id").asText())
                        .header("Authorization", "Bearer " + fixture.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(payment.path("id").asText()));

        postWebhook("evt-bad-" + UUID.randomUUID(), "payment_intent.succeeded", providerRef, 3_200L, "succeeded", "nope")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
        assertThat(orderStatus(fixture.orderId())).isEqualTo("PENDING_PAYMENT");

        postWebhook("evt-tamper-" + UUID.randomUUID(), "payment_intent.succeeded", providerRef, 1L, "succeeded", "test_sig")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        assertThat(orderStatus(fixture.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(inventoryCommitted(fixture.orderId())).isFalse();

        String eventId = "evt-ok-" + UUID.randomUUID();
        postWebhook(eventId, "payment_intent.succeeded", providerRef, 6_400L, "succeeded", "test_sig")
                .andExpect(status().isOk());
        postWebhook(eventId, "payment_intent.succeeded", providerRef, 6_400L, "succeeded", "test_sig")
                .andExpect(status().isOk());

        assertThat(orderStatus(fixture.orderId())).isEqualTo("PAID");
        assertThat(inventoryCommitted(fixture.orderId())).isTrue();
        assertThat(reserved(fixture.productId())).isZero();
        assertThat(paymentStatus(payment.path("id").asText())).isEqualTo("SUCCEEDED");
        Integer refunds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refunds WHERE payment_id = ?",
                Integer.class,
                UUID.fromString(payment.path("id").asText()));
        assertThat(refunds).isZero();
        Integer notices = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_logs WHERE template_code = 'order.paid' AND subject LIKE ?",
                Integer.class,
                "%Order paid " + fixture.orderId() + "%");
        assertThat(notices).isEqualTo(1);
        Integer published = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'order.paid' AND status = 'PUBLISHED' AND payload LIKE ?",
                Integer.class,
                "%" + fixture.orderId() + "%");
        assertThat(published).isEqualTo(1);
    }

    @Test
    void failureCompensatesAndCancelThenLateSuccessRefundsOnce() throws Exception {
        Fixture failed = checkoutFixture(1_100L, 1);
        JsonNode failedPay = pay(failed.customerToken(), failed.orderId());
        postWebhook(
                "evt-fail-" + UUID.randomUUID(),
                "payment_intent.payment_failed",
                failedPay.path("providerRef").asText(),
                1_100L,
                "requires_payment_method",
                "test_sig")
                .andExpect(status().isOk());
        assertThat(orderStatus(failed.orderId())).isEqualTo("PAYMENT_FAILED");
        assertThat(inventoryReleased(failed.orderId())).isTrue();
        assertThat(inventoryCommitted(failed.orderId())).isFalse();
        assertThat(reserved(failed.productId())).isZero();
        assertThat(paymentStatus(failedPay.path("id").asText())).isEqualTo("FAILED");

        mockMvc.perform(post("/api/v1/orders/" + failed.orderId() + "/pay")
                        .header("Authorization", "Bearer " + failed.customerToken()))
                .andExpect(status().isConflict());

        Fixture cancelled = checkoutFixture(4_200L, 1);
        JsonNode cancelledPay = pay(cancelled.customerToken(), cancelled.orderId());
        mockMvc.perform(post("/api/v1/orders/" + cancelled.orderId() + "/cancel")
                        .header("Authorization", "Bearer " + cancelled.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        String eventId = "evt-late-" + UUID.randomUUID();
        postWebhook(eventId, "payment_intent.succeeded", cancelledPay.path("providerRef").asText(), 4_200L, "succeeded", "test_sig")
                .andExpect(status().isOk());
        postWebhook(eventId, "payment_intent.succeeded", cancelledPay.path("providerRef").asText(), 4_200L, "succeeded", "test_sig")
                .andExpect(status().isOk());

        assertThat(orderStatus(cancelled.orderId())).isEqualTo("CANCELLED");
        assertThat(inventoryCommitted(cancelled.orderId())).isFalse();
        assertThat(reserved(cancelled.productId())).isZero();
        assertThat(paymentStatus(cancelledPay.path("id").asText())).isEqualTo("SUCCEEDED_NEEDS_REFUND");
        Integer refunds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refunds WHERE payment_id = ?",
                Integer.class,
                UUID.fromString(cancelledPay.path("id").asText()));
        assertThat(refunds).isEqualTo(1);
        String refundStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM refunds WHERE payment_id = ?",
                String.class,
                UUID.fromString(cancelledPay.path("id").asText()));
        assertThat(refundStatus).isEqualTo("REFUNDED");
    }

    @Test
    void uncertainLateRefundRequiresReconciliationAndDoesNotDuplicate() throws Exception {
        Fixture cancelled = checkoutFixture(2_500L, 1);
        JsonNode cancelledPay = pay(cancelled.customerToken(), cancelled.orderId());
        mockMvc.perform(post("/api/v1/orders/" + cancelled.orderId() + "/cancel")
                        .header("Authorization", "Bearer " + cancelled.customerToken()))
                .andExpect(status().isOk());

        fakePaymentProvider.failNextRefundAsUncertain();
        String eventId = "evt-unc-" + UUID.randomUUID();
        postWebhook(eventId, "payment_intent.succeeded", cancelledPay.path("providerRef").asText(), 2_500L, "succeeded", "test_sig")
                .andExpect(status().isOk());
        postWebhook(eventId, "payment_intent.succeeded", cancelledPay.path("providerRef").asText(), 2_500L, "succeeded", "test_sig")
                .andExpect(status().isOk());

        assertThat(orderStatus(cancelled.orderId())).isEqualTo("CANCELLED");
        assertThat(inventoryCommitted(cancelled.orderId())).isFalse();
        Integer refunds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refunds WHERE payment_id = ?",
                Integer.class,
                UUID.fromString(cancelledPay.path("id").asText()));
        assertThat(refunds).isEqualTo(1);
        String refundStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM refunds WHERE payment_id = ?",
                String.class,
                UUID.fromString(cancelledPay.path("id").asText()));
        assertThat(refundStatus).isEqualTo("RECONCILIATION_REQUIRED");
    }

    private org.springframework.test.web.servlet.ResultActions postWebhook(
            String eventId,
            String type,
            String providerRef,
            long amount,
            String providerStatus,
            String signature) throws Exception {
        String body = """
                {"id":"%s","type":"%s","data":{"object":{"id":"%s","amount":%d,"currency":"usd","status":"%s"}}}
                """.formatted(eventId, type, providerRef, amount, providerStatus);
        return mockMvc.perform(post("/api/v1/payments/webhook/stripe")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode pay(String token, String orderId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String orderStatus(String orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                UUID.fromString(orderId));
    }

    private String paymentStatus(String paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE id = ?",
                String.class,
                UUID.fromString(paymentId));
    }

    private boolean inventoryCommitted(String orderId) {
        Boolean value = jdbcTemplate.queryForObject(
                "SELECT inventory_committed FROM orders WHERE id = ?",
                Boolean.class,
                UUID.fromString(orderId));
        return Boolean.TRUE.equals(value);
    }

    private boolean inventoryReleased(String orderId) {
        Boolean value = jdbcTemplate.queryForObject(
                "SELECT inventory_released FROM orders WHERE id = ?",
                Boolean.class,
                UUID.fromString(orderId));
        return Boolean.TRUE.equals(value);
    }

    private int reserved(String productId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT quantity_reserved FROM inventory WHERE product_id = ?",
                Integer.class,
                UUID.fromString(productId));
        return value == null ? 0 : value;
    }

    private Fixture checkoutFixture(long priceCents, int quantity) throws Exception {
        String adminToken = loginAsAdminOnly();
        String customerToken = registerCustomer("pay-" + UUID.randomUUID() + "@example.com");
        String categoryId = createCategory(adminToken);
        String productId = createProduct(adminToken, categoryId, priceCents);
        seedStock(adminToken, productId, 5);
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": "%s", "quantity": %d }
                                """.formatted(productId, quantity)))
                .andExpect(status().isOk());
        MvcResult checkout = mockMvc.perform(post("/api/v1/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "pay-idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = objectMapper.readTree(checkout.getResponse().getContentAsString())
                .path("data").path("id").asText();
        return new Fixture(adminToken, customerToken, productId, orderId);
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

    private String createCategory(String adminToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "PayCat", "slug": "pay-%s", "active": true }
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
        String email = "admin-pay-" + UUID.randomUUID() + "@example.com";
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

    private record Fixture(String adminToken, String customerToken, String productId, String orderId) {
    }
}
