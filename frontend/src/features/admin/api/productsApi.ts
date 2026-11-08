import { apiRequest } from '../../../shared/lib/apiClient';
import type { PageResponse } from '../../../shared/types/paging';
import type { ProductDetail, ProductStatus, ProductSummary } from '../../../shared/types/catalog';
import type { ProductRequest } from '../types/adminTypes';

export function fetchAdminProducts(
  opts: {
    q?: string;
    categoryId?: string;
    status?: ProductStatus;
    page?: number;
    size?: number;
  } = {},
): Promise<PageResponse<ProductSummary>> {
  const params = new URLSearchParams({
    page: String(opts.page ?? 0),
    size: String(opts.size ?? 20),
  });
  if (opts.q?.trim()) params.set('q', opts.q.trim());
  if (opts.categoryId) params.set('categoryId', opts.categoryId);
  if (opts.status) params.set('status', opts.status);
  return apiRequest<PageResponse<ProductSummary>>(`/admin/products?${params.toString()}`);
}

export function fetchAdminProduct(id: string): Promise<ProductDetail> {
  return apiRequest<ProductDetail>(`/admin/products/${encodeURIComponent(id)}`);
}

export function createAdminProduct(body: ProductRequest): Promise<ProductDetail> {
  return apiRequest<ProductDetail>('/admin/products', { method: 'POST', body });
}

export function updateAdminProduct(id: string, body: ProductRequest): Promise<ProductDetail> {
  return apiRequest<ProductDetail>(`/admin/products/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body,
  });
}

export function deleteAdminProduct(id: string): Promise<void> {
  return apiRequest<void>(`/admin/products/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
