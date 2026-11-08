package com.ecommerce.platform.order.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDomainTest {

    @Test
    void orderItemComputesLineTotalInIntegerCentsWithoutFloatingPoint() {
        Order order = baseOrder(10_00L, 0L, 0L, 10_00L);
        OrderItem item = new OrderItem(
                UUID.randomUUID(),
                order,
                UUID.randomUUID(),
                "Widget",
                "SKU-1",
                1999L,
                3
        );
        assertThat(item.getUnitPriceCents()).isEqualTo(1999L);
        assertThat(item.getLineTotalCents()).isEqualTo(5997L);
        assertThat(item.getLineTotalCents()).isEqualTo(item.getUnitPriceCents() * item.getQuantity());
    }

    @Test
    void orderItemRejectsNegativeMoneyAndNonPositiveQuantity() {
        Order order = baseOrder(0L, 0L, 0L, 0L);
        UUID productId = UUID.randomUUID();
        assertThatThrownBy(() -> new OrderItem(
                UUID.randomUUID(), order, productId, "X", "S", -1L, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrderItem(
                UUID.randomUUID(), order, productId, "X", "S", 100L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void taxDefaultsToZeroInPhase5ModelConstruction() {
        Order order = baseOrder(5000L, 500L, 0L, 4500L);
        assertThat(order.getTaxCents()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    void applyAdminStatusAllowsCancelledAndRejectsPaid() {
        Order order = baseOrder(100L, 0L, 0L, 100L);
        order.applyAdminStatus(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        Order pending = baseOrder(100L, 0L, 0L, 100L);
        assertThatThrownBy(() -> pending.applyAdminStatus(OrderStatus.PAID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void customerCancelFromPendingPaymentOnly() {
        Order order = baseOrder(100L, 0L, 0L, 100L);
        order.applyCustomerCancel();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(order::applyCustomerCancel).isInstanceOf(IllegalStateException.class);
    }

    private static Order baseOrder(long subtotal, long discount, long tax, long total) {
        return new Order(
                UUID.randomUUID(),
                "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                UUID.randomUUID(),
                OrderStatus.PENDING_PAYMENT,
                subtotal,
                discount,
                tax,
                total,
                "USD"
        );
    }
}
