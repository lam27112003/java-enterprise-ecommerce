package com.ecommerce.platform.outbox.service;

import com.ecommerce.platform.outbox.domain.OutboxEvent;
import com.ecommerce.platform.outbox.domain.OutboxEventTypes;
import com.ecommerce.platform.outbox.domain.OutboxStatus;
import com.ecommerce.platform.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int MAX_ATTEMPTS = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final DomainEventRouter domainEventRouter;
    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplates;
    private final TransactionTemplate requiresNew;
    private final boolean kafkaEnabled;
    private final String orderPaidTopic;
    private final String paymentSucceededTopic;
    private final String paymentFailedTopic;
    private final String refundRequestedTopic;
    private final ReentrantLock lock = new ReentrantLock();

    public OutboxDispatcher(
            OutboxEventRepository outboxEventRepository,
            DomainEventRouter domainEventRouter,
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplates,
            PlatformTransactionManager transactionManager,
            @Value("${app.kafka.enabled:true}") boolean kafkaEnabled,
            @Value("${app.kafka.topics.order-paid:order.paid}") String orderPaidTopic,
            @Value("${app.kafka.topics.payment-succeeded:payment.succeeded}") String paymentSucceededTopic,
            @Value("${app.kafka.topics.payment-failed:payment.failed}") String paymentFailedTopic,
            @Value("${app.kafka.topics.refund-requested:refund.requested}") String refundRequestedTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.domainEventRouter = domainEventRouter;
        this.kafkaTemplates = kafkaTemplates;
        this.kafkaEnabled = kafkaEnabled;
        this.orderPaidTopic = orderPaidTopic;
        this.paymentSucceededTopic = paymentSucceededTopic;
        this.paymentFailedTopic = paymentFailedTopic;
        this.refundRequestedTopic = refundRequestedTopic;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.dispatch-delay-ms:2000}",
            initialDelayString = "${app.outbox.dispatch-initial-delay-ms:${app.outbox.dispatch-delay-ms:2000}}")
    public void scheduledDispatch() {
        dispatchPending();
    }

    public void dispatchPending() {
        lock.lock();
        try {
            List<OutboxEvent> pending = requiresNew.execute(status ->
                    outboxEventRepository.findTop25ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING));
            if (pending == null || pending.isEmpty()) {
                return;
            }
            for (OutboxEvent event : pending) {
                dispatchOne(event);
            }
        } finally {
            lock.unlock();
        }
    }

    private void dispatchOne(OutboxEvent event) {
        try {
            if (publishToKafka(event)) {
                markPublished(event.getId());
                return;
            }
            domainEventRouter.routePayload(event.getPayload());
            markPublished(event.getId());
        } catch (Exception ex) {
            log.warn("outbox dispatch failed eventId={} eventType={}", event.getId(), event.getEventType());
            recordFailure(event.getId());
        }
    }

    private boolean publishToKafka(OutboxEvent event) throws Exception {
        if (!kafkaEnabled) {
            return false;
        }
        KafkaTemplate<String, String> template = kafkaTemplates.getIfAvailable();
        if (template == null) {
            return false;
        }
        template.send(topicFor(event.getEventType()), event.getAggregateId(), event.getPayload())
                .get(5, TimeUnit.SECONDS);
        return true;
    }

    private String topicFor(String eventType) {
        if (OutboxEventTypes.ORDER_PAID.equals(eventType)) {
            return orderPaidTopic;
        }
        if (OutboxEventTypes.PAYMENT_SUCCEEDED.equals(eventType)) {
            return paymentSucceededTopic;
        }
        if (OutboxEventTypes.PAYMENT_FAILED.equals(eventType)) {
            return paymentFailedTopic;
        }
        if (OutboxEventTypes.REFUND_REQUESTED.equals(eventType)) {
            return refundRequestedTopic;
        }
        return paymentSucceededTopic;
    }

    private void markPublished(java.util.UUID id) {
        requiresNew.executeWithoutResult(status -> outboxEventRepository.findById(id).ifPresent(saved -> {
            if (saved.getStatus() == OutboxStatus.PENDING) {
                saved.markPublished();
                outboxEventRepository.save(saved);
            }
        }));
    }

    private void recordFailure(java.util.UUID id) {
        requiresNew.executeWithoutResult(status -> outboxEventRepository.findById(id).ifPresent(saved -> {
            if (saved.getStatus() == OutboxStatus.PENDING) {
                saved.recordFailure(MAX_ATTEMPTS);
                outboxEventRepository.save(saved);
            }
        }));
    }
}
