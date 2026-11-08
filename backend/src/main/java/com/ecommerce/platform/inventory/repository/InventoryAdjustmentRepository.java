package com.ecommerce.platform.inventory.repository;

import com.ecommerce.platform.inventory.domain.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    List<InventoryAdjustment> findByProductIdOrderByCreatedAtDesc(UUID productId);
}
