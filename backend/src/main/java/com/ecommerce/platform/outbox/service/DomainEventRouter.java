package com.ecommerce.platform.outbox.service;

import com.ecommerce.platform.notification.service.EmailNotificationService;
import com.ecommerce.platform.outbox.domain.OutboxEventTypes;
import com.ecommerce.platform.payment.service.RefundWorkerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * In-process and Kafka consumer entry for outbox payloads. Handlers are individually
 * idempotent (refund status machine; notification {@code processed_events}).
 */
@Service
public class DomainEventRouter {

    private final ObjectMapper objectMapper;
    private final RefundWorkerService refundWorkerService;
    private final EmailNotificationService emailNotificationService;

    public DomainEventRouter(
            ObjectMapper objectMapper,
            RefundWorkerService refundWorkerService,
            EmailNotificationService emailNotificationService) {
        this.objectMapper = objectMapper;
        this.refundWorkerService = refundWorkerService;
        this.emailNotificationService = emailNotificationService;
    }

    public void routePayload(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        String eventType = node.path("eventType").asText("");
        if (OutboxEventTypes.REFUND_REQUESTED.equals(eventType)) {
            String refundId = node.path("refundId").asText("");
            if (!refundId.isBlank()) {
                refundWorkerService.submit(UUID.fromString(refundId));
            }
            return;
        }
        if (OutboxEventTypes.ORDER_PAID.equals(eventType) || OutboxEventTypes.PAYMENT_FAILED.equals(eventType)) {
            emailNotificationService.handle(eventType, node);
        }
    }
}
