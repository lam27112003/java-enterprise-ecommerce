package com.ecommerce.platform.payment.api;

import com.ecommerce.platform.common.api.ApiResponse;
import com.ecommerce.platform.common.api.PageResponse;
import com.ecommerce.platform.common.web.ClientIpResolver;
import com.ecommerce.platform.payment.domain.RefundStatus;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.ConfirmExternalRefundRequest;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.ReopenAndSubmitRequest;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.RefundAuditResponse;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.RefundDetailResponse;
import com.ecommerce.platform.payment.dto.AdminRefundDtos.RefundSummaryResponse;
import com.ecommerce.platform.payment.service.AdminRefundService;
import com.ecommerce.platform.security.SecurityUtils;
import com.ecommerce.platform.security.ratelimit.ApiRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {

    private final AdminRefundService adminRefundService;
    private final ApiRateLimiter apiRateLimiter;
    private final ClientIpResolver clientIpResolver;

    public AdminRefundController(
            AdminRefundService adminRefundService,
            ApiRateLimiter apiRateLimiter,
            ClientIpResolver clientIpResolver) {
        this.adminRefundService = adminRefundService;
        this.apiRateLimiter = apiRateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping
    public ApiResponse<PageResponse<RefundSummaryResponse>> list(
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminRefundService.list(status, page, size));
    }

    @GetMapping("/{refundId}")
    public ApiResponse<RefundDetailResponse> get(@PathVariable UUID refundId) {
        return ApiResponse.ok(adminRefundService.getDetail(refundId));
    }

    @PostMapping("/{refundId}/confirm-external")
    public ApiResponse<Map<String, String>> confirmExternal(
            @PathVariable UUID refundId,
            @Valid @RequestBody ConfirmExternalRefundRequest request,
            HttpServletRequest httpRequest) {
        UUID accountId = SecurityUtils.currentUser().getId();
        apiRateLimiter.checkAdminRefundConfirm(accountId, clientIpResolver.resolve(httpRequest));
        adminRefundService.confirmExternal(refundId, accountId, request);
        return ApiResponse.ok(Map.of("status", "CONFIRMED"));
    }

    @PostMapping("/{refundId}/reopen-and-submit")
    public ApiResponse<Map<String, String>> reopenAndSubmit(
            @PathVariable UUID refundId,
            @Valid @RequestBody(required = false) ReopenAndSubmitRequest request,
            HttpServletRequest httpRequest) {
        UUID accountId = SecurityUtils.currentUser().getId();
        apiRateLimiter.checkAdminRefundReopen(accountId, clientIpResolver.resolve(httpRequest));
        adminRefundService.reopenAndSubmit(
                refundId,
                accountId,
                request == null ? new ReopenAndSubmitRequest(null) : request);
        return ApiResponse.ok(Map.of("status", "REOPENED"));
    }

    @GetMapping("/{refundId}/audits")
    public ApiResponse<List<RefundAuditResponse>> audits(@PathVariable UUID refundId) {
        return ApiResponse.ok(adminRefundService.listAudits(refundId));
    }
}
