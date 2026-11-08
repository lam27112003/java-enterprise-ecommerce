package com.ecommerce.platform.order.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Service-layer transition rules. Controllers must not bypass this.
 */
public final class OrderStatusTransitions {

    private static final Set<OrderStatus> UNPAID_ADMIN_TARGETS = EnumSet.of(
            OrderStatus.CANCELLED,
            OrderStatus.PAYMENT_FAILED
    );

    private static final Map<OrderStatus, OrderStatus> FULFILLMENT_EDGES = Map.of(
            OrderStatus.PAID, OrderStatus.PROCESSING,
            OrderStatus.PROCESSING, OrderStatus.SHIPPED,
            OrderStatus.SHIPPED, OrderStatus.DELIVERED
    );

    private OrderStatusTransitions() {
    }

    public static boolean isUnpaidAdminTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return from == OrderStatus.PENDING_PAYMENT && UNPAID_ADMIN_TARGETS.contains(to);
    }

    public static boolean isFulfillmentAdminTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        OrderStatus expected = FULFILLMENT_EDGES.get(from);
        return expected != null && expected == to;
    }

    /**
     * Admin may move unpaid orders to terminal unpaid states, or paid orders along fulfillment edges.
     * Transition to {@link OrderStatus#PAID} is always forbidden for admin.
     */
    public static boolean isAllowedAdminTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null || to == OrderStatus.PAID) {
            return false;
        }
        return isUnpaidAdminTransition(from, to) || isFulfillmentAdminTransition(from, to);
    }

    public static boolean requiresShippedTrackingFields(OrderStatus from, OrderStatus to) {
        return isFulfillmentAdminTransition(from, to) && to == OrderStatus.SHIPPED;
    }

    public static void requireShippedTrackingFields(String trackingNumber, String carrier) {
        if (trackingNumber == null || trackingNumber.isBlank() || carrier == null || carrier.isBlank()) {
            throw new IllegalStateException("trackingNumber and carrier are required when marking an order SHIPPED");
        }
    }

    /**
     * Customer cancel is allowed only from {@link OrderStatus#PENDING_PAYMENT} to {@link OrderStatus#CANCELLED}.
     */
    public static boolean isAllowedCustomerCancel(OrderStatus from) {
        return from == OrderStatus.PENDING_PAYMENT;
    }

    public static void requireAllowedAdminTransition(OrderStatus from, OrderStatus to) {
        if (to == OrderStatus.PAID) {
            throw new IllegalStateException(
                    "Illegal Phase 5 admin order status transition: " + from + " -> " + to);
        }
        if (!isAllowedAdminTransition(from, to)) {
            throw new IllegalStateException("Illegal admin order status transition: " + from + " -> " + to);
        }
    }

    /**
     * Verified payment success may move only {@link OrderStatus#PENDING_PAYMENT} to {@link OrderStatus#PAID}.
     */
    public static boolean isAllowedPaymentPaidTransition(OrderStatus from) {
        return from == OrderStatus.PENDING_PAYMENT;
    }

    public static void requirePaymentPaidTransition(OrderStatus from) {
        if (!isAllowedPaymentPaidTransition(from)) {
            throw new IllegalStateException(
                    "Illegal payment order status transition: " + from + " -> " + OrderStatus.PAID);
        }
    }
}
