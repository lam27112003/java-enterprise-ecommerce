import type { OrderStatus } from '../../../shared/types/order';

const FULFILLMENT: Partial<Record<OrderStatus, OrderStatus>> = {
  PAID: 'PROCESSING',
  PROCESSING: 'SHIPPED',
  SHIPPED: 'DELIVERED',
};

export function nextFulfillmentStatus(current: OrderStatus): OrderStatus | null {
  return FULFILLMENT[current] ?? null;
}

export function isUnpaidAdminTarget(status: OrderStatus): boolean {
  return status === 'CANCELLED' || status === 'PAYMENT_FAILED';
}

export function requiresShippedTracking(from: OrderStatus, to: OrderStatus): boolean {
  return from === 'PROCESSING' && to === 'SHIPPED';
}

export function validateShippedFields(trackingNumber: string, carrier: string): string | null {
  if (!trackingNumber.trim() || !carrier.trim()) {
    return 'Tracking number and carrier are required when marking an order SHIPPED.';
  }
  return null;
}
