package com.ecommerce.platform.promotion.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.promotion.dto.PromotionDtos.PromotionRequest;
import com.ecommerce.platform.promotion.dto.PromotionDtos.PromotionResponse;
import com.ecommerce.platform.promotion.service.PromotionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/promotions")
public class AdminPromotionController {

    private final PromotionService promotionService;

    public AdminPromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PromotionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(promotionService.list(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<PromotionResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(promotionService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PromotionResponse> create(@Valid @RequestBody PromotionRequest request) {
        return ApiResponse.ok(promotionService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PromotionResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PromotionRequest request) {
        return ApiResponse.ok(promotionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        promotionService.delete(id);
    }
}
