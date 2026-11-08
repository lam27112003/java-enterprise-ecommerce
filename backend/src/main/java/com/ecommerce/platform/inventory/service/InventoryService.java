package com.ecommerce.platform.inventory.service;

import com.ecommerce.platform.catalog.domain.Product;
import com.ecommerce.platform.catalog.repository.ProductRepository;
import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.common.exception.BusinessException;
import com.ecommerce.platform.inventory.domain.Inventory;
import com.ecommerce.platform.inventory.domain.InventoryAdjustment;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryAdjustRequest;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryAdjustmentResponse;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryResponse;
import com.ecommerce.platform.inventory.exception.InventoryExceptions;
import com.ecommerce.platform.inventory.repository.InventoryAdjustmentRepository;
import com.ecommerce.platform.inventory.repository.InventoryRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Inventory mutations for reserve/release/commit join the caller's transaction (P5-D9).
 * Admin {@link #adjust} keeps its own retrying transaction because it is not part of checkout.
 */
@Service
public class InventoryService {

    private static final int MAX_OPTIMISTIC_RETRIES = 20;

    private final InventoryRepository inventoryRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryAdjustmentRepository adjustmentRepository,
            ProductRepository productRepository,
            PlatformTransactionManager transactionManager) {
        this.inventoryRepository = inventoryRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.productRepository = productRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public void ensureInventoryExists(UUID productId) {
        if (!inventoryRepository.existsById(productId)) {
            inventoryRepository.save(new Inventory(productId));
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryResponse> list(UUID productId, int page, int size) {
        Page<Inventory> result = inventoryRepository.findAllFiltered(
                productId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("productId")));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public InventoryResponse get(UUID productId) {
        return toResponse(requireInventory(productId));
    }

    public InventoryAdjustmentResponse adjust(UUID productId, UUID actorUserId, InventoryAdjustRequest request) {
        if (request.delta() == null || request.delta() == 0) {
            throw InventoryExceptions.invalidAdjustment();
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty()) {
            throw InventoryExceptions.invalidAdjustment();
        }
        if (!productRepository.existsById(productId)) {
            throw InventoryExceptions.notFound();
        }

        return withOptimisticRetry(() -> transactionTemplate.execute(status -> {
            ensureInventoryExists(productId);
            Inventory inventory = requireInventory(productId);
            long before = inventory.getQuantityAvailable();
            if (before + request.delta() < 0) {
                throw InventoryExceptions.negativeAdjustment();
            }
            try {
                inventory.applyAvailableDelta(request.delta());
            } catch (IllegalStateException ex) {
                throw InventoryExceptions.negativeAdjustment();
            }
            inventoryRepository.saveAndFlush(inventory);
            InventoryAdjustment adjustment = new InventoryAdjustment(
                    UUID.randomUUID(),
                    productId,
                    actorUserId,
                    request.delta(),
                    before,
                    inventory.getQuantityAvailable(),
                    reason
            );
            adjustmentRepository.saveAndFlush(adjustment);
            Instant createdAt = adjustment.getCreatedAt() == null ? Instant.now() : adjustment.getCreatedAt();
            return new InventoryAdjustmentResponse(
                    adjustment.getId(),
                    adjustment.getProductId(),
                    adjustment.getActorUserId(),
                    adjustment.getDelta(),
                    adjustment.getQuantityAvailableBefore(),
                    adjustment.getQuantityAvailableAfter(),
                    adjustment.getReason(),
                    createdAt
            );
        }));
    }

    /** Joins the caller's transaction (P5-D9). Not exposed over HTTP. */
    @Transactional
    public void reserve(UUID productId, long quantity) {
        mutateInTx(productId, inv -> {
            try {
                inv.reserve(quantity);
            } catch (IllegalStateException ex) {
                throw InventoryExceptions.insufficientAvailable();
            }
        });
    }

    /** Joins the caller's transaction (P5-D9). Not exposed over HTTP. */
    @Transactional
    public void release(UUID productId, long quantity) {
        mutateInTx(productId, inv -> {
            try {
                inv.release(quantity);
            } catch (IllegalStateException ex) {
                throw InventoryExceptions.insufficientReserved();
            }
        });
    }

    /** Joins the caller's transaction (P5-D9). Used by Phase 6 paid path. */
    @Transactional
    public void commit(UUID productId, long quantity) {
        mutateInTx(productId, inv -> {
            try {
                inv.commit(quantity);
            } catch (IllegalStateException ex) {
                throw InventoryExceptions.insufficientReserved();
            }
        });
    }

    @Transactional(readOnly = true)
    public long getAvailableQuantity(UUID productId) {
        return requireInventory(productId).getQuantityAvailable();
    }

    private void mutateInTx(UUID productId, Consumer<Inventory> mutation) {
        Inventory inventory = requireInventory(productId);
        mutation.accept(inventory);
        inventoryRepository.saveAndFlush(inventory);
    }

    private <T> T withOptimisticRetry(java.util.concurrent.Callable<T> action) {
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_RETRIES; attempt++) {
            try {
                return action.call();
            } catch (BusinessException ex) {
                throw ex;
            } catch (OptimisticLockingFailureException ex) {
                try {
                    Thread.sleep(5L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw InventoryExceptions.concurrentModification();
                }
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
        throw InventoryExceptions.concurrentModification();
    }

    private Inventory requireInventory(UUID productId) {
        return inventoryRepository.findById(productId).orElseThrow(InventoryExceptions::notFound);
    }

    private InventoryResponse toResponse(Inventory inventory) {
        Product product = productRepository.findById(inventory.getProductId()).orElse(null);
        return new InventoryResponse(
                inventory.getProductId(),
                product == null ? null : product.getSku(),
                product == null ? null : product.getName(),
                inventory.getQuantityAvailable(),
                inventory.getQuantityReserved(),
                inventory.getVersion(),
                inventory.getUpdatedAt()
        );
    }
}
