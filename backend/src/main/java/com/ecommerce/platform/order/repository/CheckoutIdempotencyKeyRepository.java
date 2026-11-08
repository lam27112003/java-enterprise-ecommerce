package com.ecommerce.platform.order.repository;

import com.ecommerce.platform.order.domain.CheckoutIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CheckoutIdempotencyKeyRepository extends JpaRepository<CheckoutIdempotencyKey, UUID> {

    Optional<CheckoutIdempotencyKey> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
