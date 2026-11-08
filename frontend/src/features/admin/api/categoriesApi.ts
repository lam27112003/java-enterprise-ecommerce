import { apiRequest } from '../../../shared/lib/apiClient';
import type { Category } from '../../../shared/types/catalog';
import type { CategoryRequest } from '../types/adminTypes';

export function fetchAdminCategories(): Promise<Category[]> {
  return apiRequest<Category[]>('/admin/categories');
}

export function fetchAdminCategory(id: string): Promise<Category> {
  return apiRequest<Category>(`/admin/categories/${encodeURIComponent(id)}`);
}

export function createAdminCategory(body: CategoryRequest): Promise<Category> {
  return apiRequest<Category>('/admin/categories', { method: 'POST', body });
}

export function updateAdminCategory(id: string, body: CategoryRequest): Promise<Category> {
  return apiRequest<Category>(`/admin/categories/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body,
  });
}

export function deleteAdminCategory(id: string): Promise<void> {
  return apiRequest<void>(`/admin/categories/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
