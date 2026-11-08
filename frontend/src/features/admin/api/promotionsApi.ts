import { apiRequest } from '../../../shared/lib/apiClient';
import type { PageResponse } from '../../../shared/types/paging';
import type { Promotion, PromotionRequest } from '../types/adminTypes';

export function fetchAdminPromotions(page = 0, size = 20): Promise<PageResponse<Promotion>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return apiRequest<PageResponse<Promotion>>(`/admin/promotions?${params.toString()}`);
}

export function fetchAdminPromotion(id: string): Promise<Promotion> {
  return apiRequest<Promotion>(`/admin/promotions/${encodeURIComponent(id)}`);
}

export function createAdminPromotion(body: PromotionRequest): Promise<Promotion> {
  return apiRequest<Promotion>('/admin/promotions', { method: 'POST', body });
}

export function updateAdminPromotion(id: string, body: PromotionRequest): Promise<Promotion> {
  return apiRequest<Promotion>(`/admin/promotions/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body,
  });
}

export function deleteAdminPromotion(id: string): Promise<void> {
  return apiRequest<void>(`/admin/promotions/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
