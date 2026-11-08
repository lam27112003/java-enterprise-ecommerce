import { apiRequest } from '../../../shared/lib/apiClient';
import type { PageResponse } from '../../../shared/types/paging';
import type {
  ConfirmExternalRefundRequest,
  RefundAudit,
  RefundDetail,
  RefundStatus,
  RefundSummary,
  ReopenAndSubmitRequest,
} from '../types/adminTypes';

export function fetchAdminRefunds(
  status?: RefundStatus,
  page = 0,
  size = 20,
): Promise<PageResponse<RefundSummary>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return apiRequest<PageResponse<RefundSummary>>(`/admin/refunds?${params.toString()}`);
}

export function fetchAdminRefund(refundId: string): Promise<RefundDetail> {
  return apiRequest<RefundDetail>(`/admin/refunds/${encodeURIComponent(refundId)}`);
}

export function confirmExternalRefund(
  refundId: string,
  body: ConfirmExternalRefundRequest,
): Promise<{ status: string }> {
  return apiRequest<{ status: string }>(
    `/admin/refunds/${encodeURIComponent(refundId)}/confirm-external`,
    { method: 'POST', body },
  );
}

export function reopenAndSubmitRefund(
  refundId: string,
  body?: ReopenAndSubmitRequest,
): Promise<{ status: string }> {
  return apiRequest<{ status: string }>(
    `/admin/refunds/${encodeURIComponent(refundId)}/reopen-and-submit`,
    { method: 'POST', body: body ?? {} },
  );
}

export function fetchRefundAudits(refundId: string): Promise<RefundAudit[]> {
  return apiRequest<RefundAudit[]>(`/admin/refunds/${encodeURIComponent(refundId)}/audits`);
}
