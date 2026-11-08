import { apiRequest } from '../../../shared/lib/apiClient';
import type { PageResponse } from '../../../shared/types/paging';
import type { AdminUser, UserStatus } from '../types/adminTypes';

export function fetchAdminUsers(
  q?: string,
  page = 0,
  size = 20,
): Promise<PageResponse<AdminUser>> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  if (q?.trim()) params.set('q', q.trim());
  return apiRequest<PageResponse<AdminUser>>(`/admin/users?${params.toString()}`);
}

export function updateAdminUserStatus(userId: string, status: UserStatus): Promise<AdminUser> {
  return apiRequest<AdminUser>(`/admin/users/${encodeURIComponent(userId)}/status`, {
    method: 'PATCH',
    body: { status },
  });
}
