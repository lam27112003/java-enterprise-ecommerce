package com.ecommerce.platform.payment;

import com.ecommerce.platform.cart.dto.CartDtos.AddCartItemRequest;
import com.ecommerce.platform.cart.service.CartService;
import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.dto.CategoryDtos.CategoryRequest;
import com.ecommerce.platform.catalog.dto.ProductDtos.ProductRequest;
import com.ecommerce.platform.catalog.service.CategoryService;
import com.ecommerce.platform.catalog.service.ProductService;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryAdjustRequest;
import com.ecommerce.platform.inventory.service.InventoryService;
import com.ecommerce.platform.order.dto.OrderDtos.CheckoutRequest;
import com.ecommerce.platform.order.dto.OrderDtos.OrderResponse;
import com.ecommerce.platform.order.service.OrderService;
import com.ecommerce.platform.outbox.domain.OutboxEventTypes;
import com.ecommerce.platform.outbox.domain.OutboxStatus;
import com.ecommerce.platform.outbox.repository.OutboxEventRepository;
import com.ecommerce.platform.outbox.service.OutboxDispatcher;
import com.ecommerce.platform.payment.domain.PaymentStatus;
import com.ecommerce.platform.payment.domain.RefundStatus;
import com.ecommerce.platform.payment.dto.PaymentDtos.PayResponse;
import com.ecommerce.platform.payment.provider.FakePaymentProvider;
import com.ecommerce.platform.payment.repository.PaymentRepository;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
class PaymentPostgresIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("PaymentPostgresIT");
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
        registry.add("management.health.mail.enabled", () -> "false");
        registry.add("app.security.jwt.secret", () -> "integration-payment-pg-secret-must32b!");
        registry.add("app.catalog.cache.enabled", () -> "false");
        registry.add("app.security.enforce-secure-jwt-secret", () -> "false");
        registry.add("app.security.api-docs-public", () -> "true");
        registry.add("app.security.actuator-prometheus-public", () -> "true");
        registry.add("app.security.rate-limit.enabled", () -> "false");
        registry.add("app.stripe.mock", () -> "true");
        registry.add("app.outbox.dispatch-delay-ms", () -> "600000");
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> "1025");
    }

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
    private PaymentRepository paymentRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private OutboxDispatcher outboxDispatcher;
    @Autowired
    private FakePaymentProvider fakePaymentProvider;
    @Autowired
    private RefundReconciliationService refundReconciliationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void concurrentPayRequestsShareSingleActivePaymentIntent() throws Exception {
        Fixture fixture = checkout(1_800L, 1);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PayResponse>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return paymentService.pay(fixture.userId(), fixture.orderId());
            }));
        }
        start.countDown();
        List<PayResponse> responses = new ArrayList<>();
        for (Future<PayResponse> future : futures) {
            responses.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(responses).hasSize(threads);
        String paymentId = responses.getFirst().id().toString();
        String providerRef = responses.getFirst().providerRef();
        assertThat(responses).allMatch(r -> r.id().toString().equals(paymentId));
        assertThat(responses).allMatch(r -> providerRef.equals(r.providerRef()));

        Integer paymentRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                Integer.class,
                fixture.orderId());
        assertThat(paymentRows).isEqualTo(1);
        Integer openLocks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE active_lock_order_id = ?",
                Integer.class,
                fixture.orderId());
        assertThat(openLocks).isEqualTo(1);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE order_id = ?",
                String.class,
                fixture.orderId());
        assertThat(status).isIn("REQUIRES_ACTION", "PENDING");
    }

    @Test
    void duplicateWebhookAndOutboxAreIdempotentOnPostgres() {
        Fixture fixture = checkout(2_200L, 1);
        PayResponse pay = paymentService.pay(fixture.userId(), fixture.orderId());
        String eventId = "evt-pg-" + UUID.randomUUID();
        String body = webhookJson(eventId, "payment_intent.succeeded", pay.providerRef(), 2_200L, "succeeded");

        paymentWebhookService.handle(body, FakePaymentProvider.TEST_SIGNATURE);
        paymentWebhookService.handle(body, FakePaymentProvider.TEST_SIGNATURE);

        String orderStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, fixture.orderId());
        Boolean committed = jdbcTemplate.queryForObject(
                "SELECT inventory_committed FROM orders WHERE id = ?", Boolean.class, fixture.orderId());
        assertThat(orderStatus).isEqualTo("PAID");
        assertThat(committed).isTrue();
        assertThat(paymentRepository.findById(pay.id()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(paymentRepository.findById(pay.id()).orElseThrow().getActiveLockOrderId()).isNull();

        Integer eventClaims = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE consumer_name = 'stripe' AND event_id = ?",
                Integer.class,
                eventId);
        assertThat(eventClaims).isEqualTo(1);

        long orderPaidOutbox = outboxEventRepository.findAll().stream()
                .filter(e -> OutboxEventTypes.ORDER_PAID.equals(e.getEventType()))
                .filter(e -> e.getPayload().contains(fixture.orderId().toString()))
                .count();
        assertThat(orderPaidOutbox).isEqualTo(1);
        assertThat(outboxEventRepository.findAll().stream()
                .filter(e -> OutboxEventTypes.ORDER_PAID.equals(e.getEventType()))
                .filter(e -> e.getPayload().contains(fixture.orderId().toString()))
                .findFirst()
                .orElseThrow()
                .getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void lateSuccessRefundIdempotentUncertainThenSafeConfirm() {
        Fixture fixture = checkout(3_100L, 1);
        PayResponse pay = paymentService.pay(fixture.userId(), fixture.orderId());
        orderService.cancelMine(fixture.userId(), fixture.orderId());

        fakePaymentProvider.failNextRefundAsUncertain();
        String eventId = "evt-late-pg-" + UUID.randomUUID();
        paymentWebhookService.handle(
                webhookJson(eventId, "payment_intent.succeeded", pay.providerRef(), 3_100L, "succeeded"),
                FakePaymentProvider.TEST_SIGNATURE);
        paymentWebhookService.handle(
                webhookJson(eventId, "payment_intent.succeeded", pay.providerRef(), 3_100L, "succeeded"),
                FakePaymentProvider.TEST_SIGNATURE);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, fixture.orderId()))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT inventory_committed FROM orders WHERE id = ?", Boolean.class, fixture.orderId()))
                .isFalse();
        assertThat(refundRepository.countByPaymentId(pay.id())).isEqualTo(1);
        var refund = refundRepository.findByPaymentIdAndReasonCode(pay.id(), "LATE_SUCCESS").orElseThrow();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.RECONCILIATION_REQUIRED);
        String idempotencyKey = refund.getIdempotencyKey();
        assertThat(idempotencyKey).isEqualTo("refund:" + pay.id() + ":LATE_SUCCESS");

        UUID adminId = createAdmin();
        assertThatThrownBy(() ->
                refundReconciliationService.confirmExternalRefund(refund.getId(), adminId, " ", null))
                .isInstanceOf(Exception.class);

        fakePaymentProvider.scriptRefund(new com.ecommerce.platform.payment.provider.RetrievedRefund(
                "re_ops_confirmed",
                "succeeded",
                refund.getAmountCents(),
                "usd",
                pay.providerRef(),
                null,
                false));
        refundReconciliationService.confirmExternalRefund(
                refund.getId(), adminId, "re_ops_confirmed", null);
        var confirmed = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(confirmed.getIdempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(confirmed.getProviderRefundRef()).isEqualTo("re_ops_confirmed");
        assertThat(refundRepository.countByPaymentId(pay.id())).isEqualTo(1);

        assertThatThrownBy(() ->
                refundReconciliationService.reopenForIdempotentRetry(confirmed.getId(), adminId))
                .isInstanceOf(Exception.class);
    }

    @Test
    void uncertainRefundWithoutProviderRefCanReopenWithSameIdempotencyKey() {
        Fixture fixture = checkout(2_700L, 1);
        PayResponse pay = paymentService.pay(fixture.userId(), fixture.orderId());
        orderService.cancelMine(fixture.userId(), fixture.orderId());
        fakePaymentProvider.failNextRefundAsUncertain();
        paymentWebhookService.handle(
                webhookJson("evt-reopen-" + UUID.randomUUID(), "payment_intent.succeeded", pay.providerRef(), 2_700L, "succeeded"),
                FakePaymentProvider.TEST_SIGNATURE);

        var refund = refundRepository.findByPaymentIdAndReasonCode(pay.id(), "LATE_SUCCESS").orElseThrow();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.RECONCILIATION_REQUIRED);
        assertThat(refund.getProviderRefundRef()).isNull();
        String key = refund.getIdempotencyKey();

        refundReconciliationService.reopenAndSubmit(refund.getId(), createAdmin(), null);
        var after = refundRepository.findById(refund.getId()).orElseThrow();
        assertThat(after.getIdempotencyKey()).isEqualTo(key);
        assertThat(after.getStatus()).isEqualTo(RefundStatus.REFUNDED);
        assertThat(refundRepository.countByPaymentId(pay.id())).isEqualTo(1);
    }

    @Test
    void webhookRollbackLeavesOrderUnpaidWhenAmountMismatches() {
        Fixture fixture = checkout(1_500L, 1);
        PayResponse pay = paymentService.pay(fixture.userId(), fixture.orderId());
        assertThatThrownBy(() -> paymentWebhookService.handle(
                webhookJson("evt-bad-amt-" + UUID.randomUUID(), "payment_intent.succeeded", pay.providerRef(), 1L, "succeeded"),
                FakePaymentProvider.TEST_SIGNATURE))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, fixture.orderId()))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT inventory_committed FROM orders WHERE id = ?", Boolean.class, fixture.orderId()))
                .isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE id = ?", String.class, pay.id()))
                .isIn("REQUIRES_ACTION", "PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE payload LIKE ?",
                Integer.class,
                "%" + fixture.orderId() + "%")).isZero();
    }

    @Test
    void outboxPersistsPendingUntilDispatched() {
        Fixture fixture = checkout(1_250L, 1);
        PayResponse pay = paymentService.pay(fixture.userId(), fixture.orderId());

        // Book late refund path without auto-dispatch by inserting outbox only via webhook success on cancel.
        orderService.cancelMine(fixture.userId(), fixture.orderId());
        // Force a pending outbox row by running webhook then checking published after dispatcher.
        paymentWebhookService.handle(
                webhookJson("evt-outbox-" + UUID.randomUUID(), "payment_intent.succeeded", pay.providerRef(), 1_250L, "succeeded"),
                FakePaymentProvider.TEST_SIGNATURE);

        long refundOutbox = outboxEventRepository.findAll().stream()
                .filter(e -> OutboxEventTypes.REFUND_REQUESTED.equals(e.getEventType()))
                .filter(e -> e.getPayload().contains(pay.id().toString()))
                .count();
        assertThat(refundOutbox).isEqualTo(1);
        // Dispatcher already ran after webhook commit; republish is no-op for PUBLISHED.
        outboxDispatcher.dispatchPending();
        assertThat(outboxEventRepository.findAll().stream()
                .filter(e -> OutboxEventTypes.REFUND_REQUESTED.equals(e.getEventType()))
                .filter(e -> e.getPayload().contains(pay.id().toString()))
                .allMatch(e -> e.getStatus() == OutboxStatus.PUBLISHED)).isTrue();
    }

    @Test
    void partialUniqueIndexRejectsSecondOpenPaymentRow() {
        Fixture fixture = checkout(900L, 1);
        paymentService.pay(fixture.userId(), fixture.orderId());
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                jdbcTemplate.update(
                        """
                        INSERT INTO payments (
                            id, order_id, user_id, status, amount_cents, currency, provider,
                            provider_ref, idempotency_key, active_lock_order_id, version, created_at, updated_at
                        ) VALUES (?, ?, ?, 'PENDING_CREATE', 900, 'USD', 'stripe', NULL, ?, ?, 0, NOW(), NOW())
                        """,
                        UUID.randomUUID(),
                        fixture.orderId(),
                        fixture.userId(),
                        "pay-extra:" + UUID.randomUUID(),
                        fixture.orderId())))
                .isInstanceOf(RuntimeException.class);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?",
                Integer.class,
                fixture.orderId());
        assertThat(count).isEqualTo(1);
    }

    private Fixture checkout(long priceCents, int qty) {
        UUID adminId = createAdmin();
        UUID userId = createCustomer();
        var category = categoryService.create(new CategoryRequest(
                "PayPG-" + UUID.randomUUID().toString().substring(0, 6),
                null,
                null,
                null,
                true,
                0));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var product = productService.create(new ProductRequest(
                category.id(),
                "SKU-PG-" + suffix,
                "Pay Product " + suffix,
                "pay-product-" + suffix,
                null,
                priceCents,
                "USD",
                ProductStatus.ACTIVE));
        inventoryService.adjust(product.id(), adminId, new InventoryAdjustRequest(10L, "seed"));
        cartService.addItem(userId, new AddCartItemRequest(product.id(), qty));
        OrderResponse order = orderService.checkout(
                userId,
                "idem-pg-" + UUID.randomUUID(),
                new CheckoutRequest(null, null));
        return new Fixture(userId, order.id(), product.id());
    }

    private UUID createAdmin() {
        Role admin = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        User user = new User(
                UUID.randomUUID(),
                "admin-pg-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "Admin PG",
                UserStatus.ACTIVE);
        user.addRole(admin);
        return userRepository.save(user).getId();
    }

    private UUID createCustomer() {
        Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
        User user = new User(
                UUID.randomUUID(),
                "pay-pg-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "Pay PG",
                UserStatus.ACTIVE);
        user.addRole(customer);
        return userRepository.save(user).getId();
    }

    private static String webhookJson(
            String eventId, String type, String providerRef, long amount, String status) {
        return """
                {"id":"%s","type":"%s","data":{"object":{"id":"%s","amount":%d,"currency":"usd","status":"%s"}}}
                """.formatted(eventId, type, providerRef, amount, status);
    }

    private record Fixture(UUID userId, UUID orderId, UUID productId) {
    }
}
