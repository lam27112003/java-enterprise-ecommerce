package com.ecommerce.platform.cart.api;

import com.ecommerce.platform.cart.dto.CartDtos.CartResponse;
import com.ecommerce.platform.cart.service.CartService;
import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.promotion.dto.PromotionDtos.PromotionPreviewResponse;
import com.ecommerce.platform.promotion.service.PromotionService;
import com.ecommerce.platform.security.SecurityUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
public class CartPromoController {

    private final CartService cartService;
    private final PromotionService promotionService;

    public CartPromoController(CartService cartService, PromotionService promotionService) {
        this.cartService = cartService;
        this.promotionService = promotionService;
    }

    public record CartPromoRequest(@NotBlank @Size(max = 64) String code) {
    }

    @PostMapping("/promo")
    public ApiResponse<PromotionPreviewResponse> applyPromoPreview(@RequestBody CartPromoRequest request) {
        CartResponse cart = cartService.getCart(SecurityUtils.currentUser().getId());
        return ApiResponse.ok(promotionService.preview(request.code(), cart.subtotalCents()));
    }
}
