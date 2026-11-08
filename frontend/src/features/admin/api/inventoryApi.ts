import { apiRequest } from '../../../shared/lib/apiClient';
import type { PageResponse } from '../../../shared/types/paging';
import type { InventoryAdjustRequest, InventoryAdjustment, InventoryRow } from '../types/adminTypes';

export function fetchAdminInventory(
  productId?: string,
  page = 0,
  size = 20,
): Promise<PageResponse<InventoryRow>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (productId) params.set('productId', productId);
  return apiRequest<PageResponse<InventoryRow>>(`/admin/inventory?${params.toString()}`);
}

export function adjustAdminInventory(
  productId: string,
  body: InventoryAdjustRequest,
): Promise<InventoryAdjustment> {
  return apiRequest<InventoryAdjustment>(`/admin/inventory/${encodeURIComponent(productId)}`, {
    method: 'PATCH',
    body,
  });
}
