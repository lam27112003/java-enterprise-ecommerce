package com.ecommerce.platform.inventory.repository;

import com.ecommerce.platform.catalog.domain.Product;
import com.ecommerce.platform.inventory.domain.Inventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("""
            SELECT i FROM Inventory i
            WHERE (:productId IS NULL OR i.productId = :productId)
            """)
    Page<Inventory> findAllFiltered(@Param("productId") UUID productId, Pageable pageable);

    @Query("""
            select count(i) from Inventory i
             join Product p on p.id = i.productId
             where p.deletedAt is null
               and i.quantityAvailable <= :threshold
            """)
    long countLowStockActiveProducts(@Param("threshold") long threshold);
}
