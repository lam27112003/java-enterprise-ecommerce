package com.ecommerce.platform.inventory.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryAdjustRequest;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryAdjustmentResponse;
import com.ecommerce.platform.inventory.dto.InventoryDtos.InventoryResponse;
import com.ecommerce.platform.inventory.service.InventoryService;
import com.ecommerce.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class AdminInventoryController {

    private final InventoryService inventoryService;

    public AdminInventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<InventoryResponse>> list(
            @RequestParam(required = false) UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inventoryService.list(productId, page, size));
    }

    @GetMapping("/{productId}")
    public ApiResponse<InventoryResponse> get(@PathVariable UUID productId) {
        return ApiResponse.ok(inventoryService.get(productId));
    }

    @PatchMapping("/{productId}")
    public ApiResponse<InventoryAdjustmentResponse> adjust(
            @PathVariable UUID productId,
            @Valid @RequestBody InventoryAdjustRequest request) {
        UUID actorId = SecurityUtils.currentUser().getId();
        return ApiResponse.ok(inventoryService.adjust(productId, actorId, request));
    }
}
