package com.ecommerce.platform.promotion.service;

import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.promotion.domain.Promotion;
import com.ecommerce.platform.promotion.domain.PromotionRedemption;
import com.ecommerce.platform.promotion.domain.PromotionType;
import com.ecommerce.platform.promotion.dto.PromotionDtos.PromotionPreviewResponse;
import com.ecommerce.platform.promotion.dto.PromotionDtos.PromotionRequest;
import com.ecommerce.platform.promotion.dto.PromotionDtos.PromotionResponse;
import com.ecommerce.platform.promotion.exception.PromotionExceptions;
import com.ecommerce.platform.promotion.repository.PromotionRedemptionRepository;
import com.ecommerce.platform.promotion.repository.PromotionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository redemptionRepository;

    public PromotionService(
            PromotionRepository promotionRepository,
            PromotionRedemptionRepository redemptionRepository) {
        this.promotionRepository = promotionRepository;
        this.redemptionRepository = redemptionRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> list(int page, int size) {
        var result = promotionRepository.findAll(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("code")));
        return PageResponse.from(result.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PromotionResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        validateRequest(request);
        if (promotionRepository.existsByCodeIgnoreCase(request.code().trim())) {
            throw PromotionExceptions.duplicateCode();
        }
        Promotion promotion = new Promotion(
                UUID.randomUUID(),
                request.code(),
                request.name(),
                request.type(),
                request.valueCentsOrPercent()
        );
        applyMutable(promotion, request);
        return toResponse(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionResponse update(UUID id, PromotionRequest request) {
        validateRequest(request);
        Promotion promotion = require(id);
        if (promotionRepository.existsByCodeIgnoreCaseAndIdNot(request.code().trim(), id)) {
            throw PromotionExceptions.duplicateCode();
        }
        promotion.setCode(request.code());
        promotion.setName(request.name());
        promotion.setType(request.type());
        promotion.setValueCentsOrPercent(request.valueCentsOrPercent());
        applyMutable(promotion, request);
        return toResponse(promotionRepository.save(promotion));
    }

    @Transactional
    public void delete(UUID id) {
        Promotion promotion = require(id);
        promotion.setActive(false);
        promotionRepository.save(promotion);
    }

    @Transactional(readOnly = true)
    public PromotionPreviewResponse preview(String code, long subtotalCents) {
        Promotion promotion = requireApplicable(code, subtotalCents, Instant.now());
        long discount = PromotionPricing.discountCents(promotion, subtotalCents);
        long tax = 0L;
        return new PromotionPreviewResponse(
                promotion.getCode(),
                subtotalCents,
                discount,
                tax,
                PromotionPricing.totalCents(subtotalCents, discount, tax),
                true
        );
    }

    /**
     * Loads and validates a promotion for checkout inside the caller's transaction.
     */
    @Transactional
    public Promotion requireApplicableForUpdate(String code, long subtotalCents) {
        return requireApplicable(code, subtotalCents, Instant.now());
    }

    @Transactional
    public void redeem(Promotion promotion, UUID userId, UUID orderId) {
        promotion.incrementUsedCount();
        promotionRepository.saveAndFlush(promotion);
        redemptionRepository.saveAndFlush(new PromotionRedemption(
                UUID.randomUUID(), promotion.getId(), userId, orderId));
    }

    /**
     * Restores usage exactly once per order redemption (P5-D10).
     */
    @Transactional
    public void restoreUsageForOrder(UUID orderId) {
        var redemption = redemptionRepository.findByOrderId(orderId).orElse(null);
        if (redemption == null || !redemption.markRestoredOnce()) {
            return;
        }
        Promotion promotion = require(redemption.getPromotionId());
        promotion.decrementUsedCount();
        promotionRepository.saveAndFlush(promotion);
        redemptionRepository.saveAndFlush(redemption);
    }

    private Promotion requireApplicable(String code, long subtotalCents, Instant now) {
        if (code == null || code.isBlank()) {
            throw PromotionExceptions.invalid();
        }
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(PromotionExceptions::notFound);
        if (!promotion.isActive() || !promotion.isWithinWindow(now)) {
            throw PromotionExceptions.inactiveOrExpired();
        }
        if (subtotalCents < promotion.getMinOrderAmountCents()) {
            throw PromotionExceptions.minOrderNotMet();
        }
        if (!promotion.hasRemainingUsage()) {
            throw PromotionExceptions.usageLimitReached();
        }
        return promotion;
    }

    private void applyMutable(Promotion promotion, PromotionRequest request) {
        promotion.setDescription(trimToNull(request.description()));
        promotion.setStartsAt(request.startsAt());
        promotion.setEndsAt(request.endsAt());
        promotion.setUsageLimit(request.usageLimit());
        promotion.setMinOrderAmountCents(request.minOrderAmountCents() == null ? 0L : request.minOrderAmountCents());
        if (request.active() != null) {
            promotion.setActive(request.active());
        }
        if (promotion.getStartsAt() != null
                && promotion.getEndsAt() != null
                && promotion.getEndsAt().isBefore(promotion.getStartsAt())) {
            throw PromotionExceptions.invalid();
        }
    }

    private void validateRequest(PromotionRequest request) {
        if (request.type() == PromotionType.PERCENT
                && (request.valueCentsOrPercent() < 0 || request.valueCentsOrPercent() > 100)) {
            throw PromotionExceptions.invalid();
        }
        if (request.type() == PromotionType.FIXED && request.valueCentsOrPercent() < 0) {
            throw PromotionExceptions.invalid();
        }
    }

    private Promotion require(UUID id) {
        return promotionRepository.findById(id).orElseThrow(PromotionExceptions::notFound);
    }

    private PromotionResponse toResponse(Promotion p) {
        return new PromotionResponse(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getType(),
                p.getValueCentsOrPercent(),
                p.getStartsAt(),
                p.getEndsAt(),
                p.getUsageLimit(),
                p.getUsedCount(),
                p.getMinOrderAmountCents(),
                p.isActive(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
