package com.ecommerce.platform.outbox.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class KafkaDomainEventListener {

    private final DomainEventRouter domainEventRouter;

    public KafkaDomainEventListener(DomainEventRouter domainEventRouter) {
        this.domainEventRouter = domainEventRouter;
    }

    @KafkaListener(topics = {
            "${app.kafka.topics.order-paid:order.paid}",
            "${app.kafka.topics.payment-succeeded:payment.succeeded}",
            "${app.kafka.topics.payment-failed:payment.failed}",
            "${app.kafka.topics.refund-requested:refund.requested}"
    })
    public void onMessage(String payload) throws Exception {
        domainEventRouter.routePayload(payload);
    }
}
