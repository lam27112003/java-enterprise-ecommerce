import { apiRequest } from '../../../shared/lib/apiClient';
import type { PageResponse } from '../../../shared/types/paging';
import type { CheckoutRequest, Order } from '../../../shared/types/order';

export function checkoutOrder(
  request: CheckoutRequest,
  idempotencyKey: string,
): Promise<Order> {
  return apiRequest<Order>('/orders/checkout', {
    method: 'POST',
    body: request,
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  });
}

export function fetchOrders(page = 0, size = 20): Promise<PageResponse<Order>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  return apiRequest<PageResponse<Order>>(`/orders?${params.toString()}`);
}

export function fetchOrder(orderId: string): Promise<Order> {
  return apiRequest<Order>(`/orders/${encodeURIComponent(orderId)}`);
}

export function cancelOrder(orderId: string): Promise<Order> {
  return apiRequest<Order>(`/orders/${encodeURIComponent(orderId)}/cancel`, {
    method: 'POST',
  });
}
