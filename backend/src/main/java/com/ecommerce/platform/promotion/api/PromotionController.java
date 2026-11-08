package com.ecommerce.platform.promotion.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.promotion.dto.PromotionDtos.PromotionPreviewResponse;
import com.ecommerce.platform.promotion.dto.PromotionDtos.ValidatePromotionRequest;
import com.ecommerce.platform.promotion.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/promotions")
@PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
public class PromotionController {

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping("/validate")
    public ApiResponse<PromotionPreviewResponse> validate(@Valid @RequestBody ValidatePromotionRequest request) {
        return ApiResponse.ok(promotionService.preview(request.code(), request.subtotalCents()));
    }
}
