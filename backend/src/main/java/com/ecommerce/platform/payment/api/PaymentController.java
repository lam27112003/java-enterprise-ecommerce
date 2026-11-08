package com.ecommerce.platform.payment.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.payment.dto.PaymentDtos.PaymentResponse;
import com.ecommerce.platform.payment.service.PaymentService;
import com.ecommerce.platform.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public ApiResponse<PaymentResponse> get(@PathVariable UUID paymentId) {
        return ApiResponse.ok(paymentService.getForUser(SecurityUtils.currentUser(), paymentId));
    }
}
