package com.ecommerce.platform.order.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.common.web.ClientIpResolver;
import com.ecommerce.platform.order.dto.OrderDtos.CheckoutRequest;
import com.ecommerce.platform.order.dto.OrderDtos.OrderResponse;
import com.ecommerce.platform.order.service.OrderService;
import com.ecommerce.platform.security.SecurityUtils;
import com.ecommerce.platform.security.ratelimit.ApiRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
public class OrderController {

    private final OrderService orderService;
    private final ApiRateLimiter apiRateLimiter;
    private final ClientIpResolver clientIpResolver;

    public OrderController(
            OrderService orderService,
            ApiRateLimiter apiRateLimiter,
            ClientIpResolver clientIpResolver) {
        this.orderService = orderService;
        this.apiRateLimiter = apiRateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> checkout(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody(required = false) CheckoutRequest request,
            HttpServletRequest httpRequest) {
        UUID accountId = SecurityUtils.currentUser().getId();
        apiRateLimiter.checkCheckout(accountId, clientIpResolver.resolve(httpRequest));
        return ApiResponse.ok(orderService.checkout(accountId, idempotencyKey, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(orderService.listMine(SecurityUtils.currentUser().getId(), page, size));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> get(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.getMine(SecurityUtils.currentUser().getId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.cancelMine(SecurityUtils.currentUser().getId(), orderId));
    }
}
