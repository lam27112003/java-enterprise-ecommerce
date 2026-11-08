package com.ecommerce.platform.notification.service;

import com.ecommerce.platform.notification.domain.NotificationLog;
import com.ecommerce.platform.notification.repository.NotificationLogRepository;
import com.ecommerce.platform.order.domain.Order;
import com.ecommerce.platform.order.repository.OrderRepository;
import com.ecommerce.platform.outbox.domain.OutboxEventTypes;
import com.ecommerce.platform.outbox.domain.ProcessedEvent;
import com.ecommerce.platform.outbox.repository.ProcessedEventRepository;
import com.ecommerce.platform.user.domain.User;
import com.ecommerce.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);
    private static final String SOURCE = "notification";

    private final JavaMailSender mailSender;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final TransactionTemplate requiresNew;
    private final String fromAddress;

    public EmailNotificationService(
            JavaMailSender mailSender,
            OrderRepository orderRepository,
            UserRepository userRepository,
            NotificationLogRepository notificationLogRepository,
            ProcessedEventRepository processedEventRepository,
            PlatformTransactionManager transactionManager,
            @Value("${app.notification.from:noreply@localhost}") String fromAddress) {
        this.mailSender = mailSender;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.processedEventRepository = processedEventRepository;
        this.fromAddress = fromAddress;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void handle(String eventType, JsonNode payload) {
        if (!OutboxEventTypes.ORDER_PAID.equals(eventType) && !OutboxEventTypes.PAYMENT_FAILED.equals(eventType)) {
            return;
        }
        String eventKey = payload.path("outboxEventId").asText("");
        if (eventKey.isBlank()) {
            return;
        }
        String orderIdText = payload.path("orderId").asText("");
        if (orderIdText.isBlank()) {
            return;
        }
        UUID orderId;
        try {
            orderId = UUID.fromString(orderIdText);
        } catch (IllegalArgumentException ex) {
            return;
        }
        boolean claimed = Boolean.TRUE.equals(requiresNew.execute(status -> claim(eventKey)));
        if (!claimed) {
            return;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            record(eventKey, "unknown", eventType, "Missing order " + orderId, NotificationLog.DeliveryStatus.FAILED);
            return;
        }
        User user = userRepository.findById(order.getUserId()).orElse(null);
        if (user == null) {
            record(eventKey, "unknown", eventType, "Missing user", NotificationLog.DeliveryStatus.FAILED);
            return;
        }
        String subject = subject(eventType, order);
        String body = body(eventType, order);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(user.getEmail());
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            record(eventKey, user.getEmail(), eventType, subject, NotificationLog.DeliveryStatus.SENT);
        } catch (RuntimeException ex) {
            log.warn("email notification failed template={} orderId={}", eventType, order.getId());
            record(eventKey, user.getEmail(), eventType, subject, NotificationLog.DeliveryStatus.FAILED);
        }
    }

    private boolean claim(String eventKey) {
        if (processedEventRepository.existsByConsumerNameAndEventId(SOURCE, eventKey)) {
            return false;
        }
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(UUID.randomUUID(), SOURCE, eventKey));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private void record(
            String eventKey,
            String recipient,
            String templateCode,
            String subject,
            NotificationLog.DeliveryStatus status) {
        requiresNew.executeWithoutResult(tx -> notificationLogRepository.save(new NotificationLog(
                UUID.randomUUID(),
                eventKey,
                recipient,
                templateCode,
                truncate(subject),
                status)));
    }

    private static String subject(String eventType, Order order) {
        if (OutboxEventTypes.ORDER_PAID.equals(eventType)) {
            return "Order paid " + order.getId();
        }
        return "Payment failed " + order.getId();
    }

    private static String body(String eventType, Order order) {
        if (OutboxEventTypes.ORDER_PAID.equals(eventType)) {
            return "Your order " + order.getOrderNumber() + " has been paid. Total "
                    + order.getTotalCents() + " " + order.getCurrency() + ".";
        }
        return "Payment for order " + order.getOrderNumber()
                + " did not succeed. Place a new order if you still want these items.";
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}
