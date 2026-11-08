package com.ecommerce.platform.order.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.order.domain.OrderStatus;
import com.ecommerce.platform.order.dto.OrderDtos.AdminStatusUpdateRequest;
import com.ecommerce.platform.order.dto.OrderDtos.OrderResponse;
import com.ecommerce.platform.order.dto.OrderDtos.OrderStatusHistoryResponse;
import com.ecommerce.platform.order.service.OrderService;
import com.ecommerce.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(orderService.listAdmin(status, page, size));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> get(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.getForAdmin(orderId));
    }

    @PatchMapping("/{orderId}/status")
    public ApiResponse<OrderResponse> updateStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminStatusUpdateRequest request) {
        return ApiResponse.ok(orderService.adminUpdateStatus(
                orderId,
                SecurityUtils.currentUser().getId(),
                request));
    }

    @GetMapping("/{orderId}/status-history")
    public ApiResponse<List<OrderStatusHistoryResponse>> statusHistory(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderService.statusHistoryForAdmin(orderId));
    }
}
