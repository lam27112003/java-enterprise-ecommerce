package com.ecommerce.platform.order.spi;

import java.util.UUID;

/**
 * Invoked only after the order transaction that moved an order to an unpaid terminal
 * status has committed. Implementations attempt PaymentIntent cancellation outside that
 * transaction (P6-D8).
 */
public interface UnpaidTerminalPaymentHook {

    void onUnpaidTerminal(UUID orderId);
}
