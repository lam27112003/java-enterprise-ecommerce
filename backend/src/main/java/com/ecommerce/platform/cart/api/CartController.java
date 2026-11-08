package com.ecommerce.platform.cart.api;

import com.ecommerce.platform.cart.dto.CartDtos.AddCartItemRequest;
import com.ecommerce.platform.cart.dto.CartDtos.CartResponse;
import com.ecommerce.platform.cart.dto.CartDtos.UpdateCartItemRequest;
import com.ecommerce.platform.cart.service.CartService;
import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartResponse> getCart() {
        return ApiResponse.ok(cartService.getCart(SecurityUtils.currentUser().getId()));
    }

    @PostMapping("/items")
    public ApiResponse<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest request) {
        return ApiResponse.ok(cartService.addItem(SecurityUtils.currentUser().getId(), request));
    }

    @PatchMapping("/items/{itemId}")
    public ApiResponse<CartResponse> updateItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return ApiResponse.ok(cartService.updateItem(SecurityUtils.currentUser().getId(), itemId, request));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CartResponse> removeItem(@PathVariable UUID itemId) {
        return ApiResponse.ok(cartService.removeItem(SecurityUtils.currentUser().getId(), itemId));
    }

    @DeleteMapping
    public ApiResponse<CartResponse> clear() {
        return ApiResponse.ok(cartService.clear(SecurityUtils.currentUser().getId()));
    }
}
