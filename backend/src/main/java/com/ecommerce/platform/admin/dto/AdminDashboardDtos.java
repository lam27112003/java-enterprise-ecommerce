package com.ecommerce.platform.admin.dto;

import java.time.Instant;
import java.util.Map;

public final class AdminDashboardDtos {

    private AdminDashboardDtos() {
    }

    public record DashboardSummaryResponse(
            long totalProducts,
            long activeProducts,
            long lowStockProducts,
            long openOrders,
            long ordersAwaitingFulfillment,
            long refundsRequiringReconciliation,
            Instant revenuePeriodFrom,
            Instant revenuePeriodTo,
            String revenuePeriodDescription,
            Map<String, Long> revenueGrossCentsByCurrency,
            Map<String, Long> revenueRefundedCentsByCurrency,
            Map<String, Long> revenueNetCentsByCurrency,
            long ordersCreatedInWindow) {
    }
}
