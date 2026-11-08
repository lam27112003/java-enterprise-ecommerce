package com.ecommerce.platform.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    public static final String PLATFORM_HEALTH_TOPIC = "platform.health";

    @Bean
    public NewTopic platformHealthTopic(
            @Value("${app.kafka.topics.platform-health:platform.health}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderPaidTopic(
            @Value("${app.kafka.topics.order-paid:order.paid}") String topicName) {
        return topic(topicName);
    }

    @Bean
    public NewTopic paymentSucceededTopic(
            @Value("${app.kafka.topics.payment-succeeded:payment.succeeded}") String topicName) {
        return topic(topicName);
    }

    @Bean
    public NewTopic paymentFailedTopic(
            @Value("${app.kafka.topics.payment-failed:payment.failed}") String topicName) {
        return topic(topicName);
    }

    @Bean
    public NewTopic refundRequestedTopic(
            @Value("${app.kafka.topics.refund-requested:refund.requested}") String topicName) {
        return topic(topicName);
    }

    private static NewTopic topic(String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
