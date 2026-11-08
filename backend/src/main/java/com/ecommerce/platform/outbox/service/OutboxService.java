package com.ecommerce.platform.outbox.service;

import com.ecommerce.platform.outbox.domain.OutboxEvent;
import com.ecommerce.platform.outbox.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OutboxEvent save(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        UUID id = UUID.randomUUID();
        Map<String, Object> body = new LinkedHashMap<>();
        if (payload != null) {
            body.putAll(payload);
        }
        body.put("eventType", eventType);
        body.put("outboxEventId", id.toString());
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize outbox payload", ex);
        }
        return outboxEventRepository.saveAndFlush(new OutboxEvent(id, aggregateType, aggregateId, eventType, json));
    }
}
