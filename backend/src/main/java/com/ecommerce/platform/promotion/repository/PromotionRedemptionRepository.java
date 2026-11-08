package com.ecommerce.platform.promotion.repository;

import com.ecommerce.platform.promotion.domain.PromotionRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, UUID> {

    Optional<PromotionRedemption> findByOrderId(UUID orderId);
}
