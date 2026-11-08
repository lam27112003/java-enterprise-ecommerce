import { apiRequest } from '../../../shared/lib/apiClient';
import type { PageResponse } from '../../../shared/types/paging';
import type { Order, OrderStatus } from '../../../shared/types/order';
import type { AdminStatusUpdateRequest, OrderStatusHistoryEntry } from '../types/adminTypes';

export function fetchAdminOrders(
  status?: OrderStatus,
  page = 0,
  size = 20,
): Promise<PageResponse<Order>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return apiRequest<PageResponse<Order>>(`/admin/orders?${params.toString()}`);
}

export function fetchAdminOrder(orderId: string): Promise<Order> {
  return apiRequest<Order>(`/admin/orders/${encodeURIComponent(orderId)}`);
}

export function updateAdminOrderStatus(
  orderId: string,
  body: AdminStatusUpdateRequest,
): Promise<Order> {
  return apiRequest<Order>(`/admin/orders/${encodeURIComponent(orderId)}/status`, {
    method: 'PATCH',
    body,
  });
}

export function fetchAdminOrderStatusHistory(orderId: string): Promise<OrderStatusHistoryEntry[]> {
  return apiRequest<OrderStatusHistoryEntry[]>(
    `/admin/orders/${encodeURIComponent(orderId)}/status-history`,
  );
}
