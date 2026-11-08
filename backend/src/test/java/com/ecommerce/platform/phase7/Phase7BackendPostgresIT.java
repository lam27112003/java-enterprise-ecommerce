package com.ecommerce.platform.phase7;

import com.ecommerce.platform.cart.dto.CartDtos.AddCartItemRequest;
import com.ecommerce.platform.cart.service.CartService;
import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductRequest;
import com.ecommerce.platform.catalog.service.CategoryService;
import com.ecommerce.platform.catalog.service.ProductService;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryAdjustRequest;
import com.ecommerce.platform.inventory.service.InventoryService;
import com.ecommerce.platform.order.domain.OrderStatus;
import com.ecommerce.platform.order.dto.OrderDtos.CheckoutRequest;
import com.ecommerce.platform.order.repository.OrderStatusHistoryRepository;
import com.ecommerce.platform.order.service.OrderService;
import com.ecommerce.platform.payment.domain.RefundStatus;
import com.ecommerce.platform.payment.dto.PaymentDtos.PayResponse;
import com.ecommerce.platform.payment.provider.FakePaymentProvider;
import com.ecommerce.platform.payment.provider.RetrievedRefund;
import com.ecommerce.platform.payment.repository.RefundRepository;
import com.ecommerce.platform.payment.service.PaymentService;
import com.ecommerce.platform.payment.service.PaymentWebhookService;
import com.ecommerce.platform.payment.service.RefundReconciliationService;
import com.ecommerce.platform.support.DockerInfrastructure;
import com.ecommerce.platform.user.domain.Role;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import com.ecommerce.platform.user.repository.RoleRepository;
import com.ecommerce.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = false)
class Phase7BackendPostgresIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("Phase7BackendPostgresIT");
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () ->
                "classpath:db/migration,classpath:db/migration_postgresql,classpath:db/testdata");
        registry.add("app.kafka.enabled", () -> "false");
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("app.security.jwt.secret", () -> "integration-phase7-pg-secret-must32b!");
        registry.add("app.security.enforce-secure-jwt-secret", () -> "false");
        registry.add("app.security.rate-limit.enabled", () -> "false");
        registry.add("app.catalog.cache.enabled", () -> "false");
        registry.add("app.stripe.mock", () -> "true");
    }

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
    private CategoryService categoryService;
    @Autowired
    private ProductService productService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private CartService cartService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentWebhookService paymentWebhookService;
    @Autowired
    private FakePaymentProvider fakePaymentProvider;
    @Autowired
    private RefundReconciliationService refundReconciliationService;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shippingFulfillmentTransitionsAppendHistory() throws Exception {
        UUID adminId = seedAdmin("ship-admin-");
        String adminToken = login(adminEmail(adminId));
        PaidOrder paid = createPaidOrder(1_800L);

        mockMvc.perform(patch("/api/v1/admin/orders/" + paid.orderId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PROCESSING", "note": "packing" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/orders/" + paid.orderId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "SHIPPED",
                                  "trackingNumber": "1Z999",
                                  "carrier": "UPS"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/admin/orders/" + paid.orderId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "DELIVERED" }
                                """))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, paid.orderId()))
                .isEqualTo("DELIVERED");
        assertThat(orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(paid.orderId())).hasSizeGreaterThanOrEqualTo(4);

        mockMvc.perform(patch("/api/v1/admin/orders/" + paid.orderId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PAID" }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentMutualAdminDisableLeavesAtLeastOneActiveAdmin() throws Exception {
        AdminPair pair = seedTwoAdmins();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Callable<Integer>> tasks = List.of(
                () -> patchUserStatus(pair.tokenA(), pair.idB(), "DISABLED"),
                () -> patchUserStatus(pair.tokenB(), pair.idA(), "DISABLED"));
        List<Future<Integer>> futures = pool.invokeAll(tasks);
        for (Future<Integer> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        long activeAdmins = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id
                WHERE r.code = 'ADMIN' AND u.status = 'ACTIVE'
                """,
                Long.class);
        assertThat(activeAdmins).isGreaterThanOrEqualTo(1);
    }

    @Test
    void refundConfirmRejectAndUncertainDoNotMarkRefunded() throws Exception {
        UUID adminId = seedAdmin("ref-admin-");
        UUID userId = seedCustomer();
        var category = categoryService.create(new CategoryRequest(
                "P7R-" + UUID.randomUUID().toString().substring(0, 6), null, null, null, true, 0));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var product = productService.create(new ProductRequest(
                category.id(),
                "SKU-P7R-" + suffix,
                "P7 Refund " + suffix,
                "p7-refund-" + suffix,
                null,
                2_200L,
                "USD",
                ProductStatus.ACTIVE));
        inventoryService.adjust(product.id(), adminId, new InventoryAdjustRequest(10L, "seed"));
        cartService.addItem(userId, new AddCartItemRequest(product.id(), 1));
        var order = orderService.checkout(userId, "idem-p7r-" + UUID.randomUUID(), new CheckoutRequest(null, null));
        PayResponse pay = paymentService.pay(userId, order.id());
        orderService.cancelMine(userId, order.id());
        fakePaymentProvider.failNextRefundAsUncertain();
        paymentWebhookService.handle(
                webhookJson("evt-p7-" + UUID.randomUUID(), "payment_intent.succeeded", pay.providerRef(), 2_200L),
                FakePaymentProvider.TEST_SIGNATURE);
        var refund = refundRepository.findByPaymentIdAndReasonCode(pay.id(), "LATE_SUCCESS").orElseThrow();

        fakePaymentProvider.scriptRefund(new RetrievedRefund(
                "re_bad_pi", "succeeded", refund.getAmountCents(), "usd", "pi_wrong", null, false));
        assertThatThrownBy(() -> refundReconciliationService.confirmExternalRefund(
                refund.getId(), adminId, "re_bad_pi", null))
                .isInstanceOf(Exception.class);
        assertThat(refundRepository.findById(refund.getId()).orElseThrow().getStatus())
                .isEqualTo(RefundStatus.RECONCILIATION_REQUIRED);

        fakePaymentProvider.failNextRetrieveRefundAsUncertain();
        assertThatThrownBy(() -> refundReconciliationService.confirmExternalRefund(
                refund.getId(), adminId, "re_missing", null))
                .isInstanceOf(Exception.class);
        assertThat(refundRepository.findById(refund.getId()).orElseThrow().getStatus())
                .isEqualTo(RefundStatus.RECONCILIATION_REQUIRED);
    }

    private int patchUserStatus(String token, UUID targetUserId, String status) throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/v1/admin/users/" + targetUserId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "%s" }
                                """.formatted(status)))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private PaidOrder createPaidOrder(long priceCents) throws Exception {
        UUID adminId = seedAdmin("ops-");
        UUID userId = seedCustomer();
        var category = categoryService.create(new CategoryRequest(
                "P7-" + UUID.randomUUID().toString().substring(0, 6), null, null, null, true, 0));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var product = productService.create(new ProductRequest(
                category.id(),
                "SKU-P7-" + suffix,
                "P7 Product " + suffix,
                "p7-product-" + suffix,
                null,
                priceCents,
                "USD",
                ProductStatus.ACTIVE));
        inventoryService.adjust(product.id(), adminId, new InventoryAdjustRequest(10L, "seed"));
        cartService.addItem(userId, new AddCartItemRequest(product.id(), 1));
        var order = orderService.checkout(userId, "idem-p7-" + UUID.randomUUID(), new CheckoutRequest(null, null));
        PayResponse pay = paymentService.pay(userId, order.id());
        paymentWebhookService.handle(
                webhookJson("evt-paid-" + UUID.randomUUID(), "payment_intent.succeeded", pay.providerRef(), priceCents),
                FakePaymentProvider.TEST_SIGNATURE);
        return new PaidOrder(userId, order.id(), pay.id(), pay.providerRef());
    }

    private UUID seedAdmin(String prefix) {
        Role admin = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        UUID id = UUID.randomUUID();
        User user = new User(
                id,
                prefix + id + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "Admin",
                UserStatus.ACTIVE);
        user.addRole(admin);
        userRepository.save(user);
        return id;
    }

    private String adminEmail(UUID adminId) {
        return userRepository.findById(adminId).orElseThrow().getEmail();
    }

    private UUID seedCustomer() {
        Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
        User user = new User(
                UUID.randomUUID(),
                "cust-p7-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "Customer",
                UserStatus.ACTIVE);
        user.addRole(customer);
        return userRepository.save(user).getId();
    }

    private AdminPair seedTwoAdmins() throws Exception {
        UUID idA = seedAdmin("adm-a-");
        UUID idB = seedAdmin("adm-b-");
        return new AdminPair(idA, idB, login(adminEmail(idA)), login(adminEmail(idB)));
    }

    private String login(String email) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "Str0ng!Pass" }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(login.getResponse().getContentAsString()).path("data");
        return data.path("accessToken").asText();
    }

    private static String webhookJson(String eventId, String type, String providerRef, long amount) {
        return """
                {"id":"%s","type":"%s","data":{"object":{"id":"%s","amount":%d,"currency":"usd","status":"succeeded"}}}
                """.formatted(eventId, type, providerRef, amount);
    }

    private record PaidOrder(UUID userId, UUID orderId, UUID paymentId, String providerRef) {
    }

    private record AdminPair(UUID idA, UUID idB, String tokenA, String tokenB) {
    }
}
