package com.ecommerce.platform.payment.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.common.web.ClientIpResolver;
import com.ecommerce.platform.payment.dto.PaymentDtos.PayResponse;
import com.ecommerce.platform.payment.dto.PaymentDtos.PaymentResponse;
import com.ecommerce.platform.payment.service.PaymentService;
import com.ecommerce.platform.security.SecurityUtils;
import com.ecommerce.platform.security.ratelimit.ApiRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
public class PaymentOrderController {

    private final PaymentService paymentService;
    private final ApiRateLimiter apiRateLimiter;
    private final ClientIpResolver clientIpResolver;

    public PaymentOrderController(
            PaymentService paymentService,
            ApiRateLimiter apiRateLimiter,
            ClientIpResolver clientIpResolver) {
        this.paymentService = paymentService;
        this.apiRateLimiter = apiRateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/{orderId}/pay")
    public ApiResponse<PayResponse> pay(@PathVariable UUID orderId, HttpServletRequest httpRequest) {
        UUID accountId = SecurityUtils.currentUser().getId();
        apiRateLimiter.checkPay(accountId, clientIpResolver.resolve(httpRequest));
        return ApiResponse.ok(paymentService.pay(accountId, orderId));
    }

    @GetMapping("/{orderId}/payment")
    public ApiResponse<PaymentResponse> payment(@PathVariable UUID orderId) {
        return ApiResponse.ok(paymentService.getForOrder(SecurityUtils.currentUser().getId(), orderId));
    }
}
