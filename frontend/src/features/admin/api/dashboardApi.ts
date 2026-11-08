import { apiRequest } from '../../../shared/lib/apiClient';
import type { DashboardSummary } from '../types/adminTypes';

export function fetchDashboardSummary(from?: string, to?: string): Promise<DashboardSummary> {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  const qs = params.toString();
  return apiRequest<DashboardSummary>(`/admin/dashboard/summary${qs ? `?${qs}` : ''}`);
}
