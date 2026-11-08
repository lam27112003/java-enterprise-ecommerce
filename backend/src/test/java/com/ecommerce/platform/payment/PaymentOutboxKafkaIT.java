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
import com.ecommerce.platform.order.service.OrderService;
import com.ecommerce.platform.outbox.domain.OutboxEventTypes;
import com.ecommerce.platform.outbox.domain.OutboxStatus;
import com.ecommerce.platform.outbox.repository.OutboxEventRepository;
import com.ecommerce.platform.payment.dto.PaymentDtos.PayResponse;
import com.ecommerce.platform.payment.provider.FakePaymentProvider;
import com.ecommerce.platform.payment.service.PaymentService;
import com.ecommerce.platform.payment.service.PaymentWebhookService;
import com.ecommerce.platform.support.DockerInfrastructure;
import com.ecommerce.platform.user.domain.Role;
import com.ecommerce.platform.user.domain.RoleCode;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.domain.UserStatus;
import com.ecommerce.platform.user.repository.RoleRepository;
import com.ecommerce.platform.user.repository.UserRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = false)
class PaymentOutboxKafkaIT {

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ecommerce")
            .withUsername("ecommerce")
            .withPassword("ecommerce");

    @Container
    @SuppressWarnings("resource")
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @BeforeAll
    static void requireDocker() {
        DockerInfrastructure.requireDocker("PaymentOutboxKafkaIT");
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
        registry.add("app.kafka.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "payment-outbox-it");
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("management.health.mail.enabled", () -> "false");
        registry.add("app.security.jwt.secret", () -> "integration-payment-kafka-secret-32b!");
        registry.add("app.catalog.cache.enabled", () -> "false");
        registry.add("app.security.enforce-secure-jwt-secret", () -> "false");
        registry.add("app.security.api-docs-public", () -> "true");
        registry.add("app.security.actuator-prometheus-public", () -> "true");
        registry.add("app.security.rate-limit.enabled", () -> "false");
        registry.add("app.stripe.mock", () -> "true");
        registry.add("app.outbox.dispatch-delay-ms", () -> "500");
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
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void paidEventPublishedToKafkaBrokerViaOutbox() {
        UUID adminId = createAdmin();
        UUID userId = createCustomer();
        var category = categoryService.create(new CategoryRequest(
                "KafkaPay", null, null, null, true, 0));
        var product = productService.create(new ProductRequest(
                category.id(),
                "SKU-K-" + UUID.randomUUID().toString().substring(0, 8),
                "Kafka Pay Product",
                null,
                null,
                1_600L,
                "USD",
                ProductStatus.ACTIVE));
        inventoryService.adjust(product.id(), adminId, new InventoryAdjustRequest(5L, "seed"));
        cartService.addItem(userId, new AddCartItemRequest(product.id(), 1));
        var order = orderService.checkout(userId, "idem-k-" + UUID.randomUUID(), new CheckoutRequest(null, null));
        PayResponse pay = paymentService.pay(userId, order.id());

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "payment-outbox-assert-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<String> payloads = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("order.paid"));
            paymentWebhookService.handle(
                    """
                    {"id":"evt-k-%s","type":"payment_intent.succeeded","data":{"object":{"id":"%s","amount":1600,"currency":"usd","status":"succeeded"}}}
                    """.formatted(UUID.randomUUID(), pay.providerRef()),
                    FakePaymentProvider.TEST_SIGNATURE);

            long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
            boolean seen = false;
            while (System.nanoTime() < deadline && !seen) {
                var records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    payloads.add(record.value());
                }
                seen = payloads.stream().anyMatch(p ->
                        p.contains(order.id().toString()) && p.contains(OutboxEventTypes.ORDER_PAID));
            }
            assertThat(seen)
                    .as("expected order.paid payload on Kafka for order %s", order.id())
                    .isTrue();
        }

        assertThat(outboxEventRepository.findAll().stream()
                .filter(e -> OutboxEventTypes.ORDER_PAID.equals(e.getEventType()))
                .filter(e -> e.getPayload().contains(order.id().toString()))
                .anyMatch(e -> e.getStatus() == OutboxStatus.PUBLISHED)).isTrue();
    }

    private UUID createAdmin() {
        Role admin = roleRepository.findByCode(RoleCode.ADMIN).orElseThrow();
        User user = new User(
                UUID.randomUUID(),
                "admin-k-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "Admin Kafka",
                UserStatus.ACTIVE);
        user.addRole(admin);
        return userRepository.save(user).getId();
    }

    private UUID createCustomer() {
        Role customer = roleRepository.findByCode(RoleCode.CUSTOMER).orElseThrow();
        User user = new User(
                UUID.randomUUID(),
                "pay-k-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "Pay Kafka",
                UserStatus.ACTIVE);
        user.addRole(customer);
        return userRepository.save(user).getId();
    }
}
