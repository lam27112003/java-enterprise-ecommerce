package com.ecommerce.platform.order.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTransitionsTest {

    @Test
    void phase5AdminMayOnlyLeavePendingPaymentToCancelledOrPaymentFailed() {
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED)).isTrue();

        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).isFalse();
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.PENDING_PAYMENT, OrderStatus.PROCESSING)).isFalse();
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT)).isFalse();
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void customerCancelOnlyFromPendingPayment() {
        assertThat(OrderStatusTransitions.isAllowedCustomerCancel(OrderStatus.PENDING_PAYMENT)).isTrue();
        assertThat(OrderStatusTransitions.isAllowedCustomerCancel(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatusTransitions.isAllowedCustomerCancel(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void requireAllowedAdminTransitionRejectsPaid() {
        assertThatThrownBy(() ->
                OrderStatusTransitions.requireAllowedAdminTransition(
                        OrderStatus.PENDING_PAYMENT, OrderStatus.PAID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAID");
    }

    @Test
    void paymentMayMarkPaidOnlyFromPendingPayment() {
        assertThat(OrderStatusTransitions.isAllowedPaymentPaidTransition(OrderStatus.PENDING_PAYMENT)).isTrue();
        assertThat(OrderStatusTransitions.isAllowedPaymentPaidTransition(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatusTransitions.isAllowedPaymentPaidTransition(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatusTransitions.isAllowedPaymentPaidTransition(OrderStatus.PAYMENT_FAILED)).isFalse();
        OrderStatusTransitions.requirePaymentPaidTransition(OrderStatus.PENDING_PAYMENT);
        assertThatThrownBy(() -> OrderStatusTransitions.requirePaymentPaidTransition(OrderStatus.CANCELLED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAID");
        assertThatThrownBy(() -> OrderStatusTransitions.requirePaymentPaidTransition(OrderStatus.PAYMENT_FAILED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fulfillmentEdgesAllowedForAdmin() {
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.PAID, OrderStatus.PROCESSING)).isTrue();
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.PROCESSING, OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.SHIPPED, OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.PAID, OrderStatus.SHIPPED)).isFalse();
        assertThat(OrderStatusTransitions.isAllowedAdminTransition(
                OrderStatus.PAID, OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void shippedRequiresTrackingAndCarrier() {
        assertThatThrownBy(() -> OrderStatusTransitions.requireShippedTrackingFields(null, "UPS"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void terminalFlagsMatchPhase5() {
        assertThat(OrderStatus.CANCELLED.isTerminalInPhase5()).isTrue();
        assertThat(OrderStatus.PAYMENT_FAILED.isTerminalInPhase5()).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.isTerminalInPhase5()).isFalse();
        assertThat(OrderStatus.PAID.isTerminalInPhase5()).isFalse();
    }
}
