package com.ecommerce.platform.outbox.repository;

import com.ecommerce.platform.outbox.domain.OutboxEvent;
import com.ecommerce.platform.outbox.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop25ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
