import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { PaginationControls } from '../../catalog/components/PaginationControls';
import { fetchAdminRefunds } from '../api/refundsApi';
import type { RefundStatus, RefundSummary } from '../types/adminTypes';

const PAGE_SIZE = 20;
const STATUSES: (RefundStatus | '')[] = [
  '',
  'REFUND_PENDING',
  'REFUND_SUBMITTED',
  'REFUNDED',
  'RECONCILIATION_REQUIRED',
];

export function AdminRefundsPage() {
  const [statusFilter, setStatusFilter] = useState<RefundStatus | ''>('');
  const [page, setPage] = useState(0);
  const [refunds, setRefunds] = useState<RefundSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchAdminRefunds(statusFilter || undefined, page, PAGE_SIZE);
      setRefunds(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load refunds');
    } finally {
      setLoading(false);
    }
  }, [statusFilter, page]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading && refunds.length === 0) {
    return <LoadingState label="Loading refunds…" />;
  }

  if (error && refunds.length === 0) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold text-brand-900">Refunds</h1>
      </header>

      <select
        value={statusFilter}
        onChange={(e) => {
          setPage(0);
          setStatusFilter(e.target.value as RefundStatus | '');
        }}
        className="rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm"
        aria-label="Filter refunds by status"
      >
        {STATUSES.map((s) => (
          <option key={s || 'all'} value={s}>
            {s || 'All'}
          </option>
        ))}
      </select>

      {refunds.length === 0 ? (
        <EmptyState title="No refunds" />
      ) : (
        <>
          <ul className="space-y-2">
            {refunds.map((r) => (
              <li key={r.id} className="rounded-2xl border border-brand-100 bg-white/80 px-4 py-3">
                <Link to={`/admin/refunds/${r.id}`} className="font-semibold text-brand-900 underline">
                  {r.id.slice(0, 8)}…
                </Link>
                <p className="font-sans text-sm text-brand-600">
                  {r.status} · {formatMoney(r.amountCents, r.currency)} · order {r.orderId.slice(0, 8)}…
                </p>
              </li>
            ))}
          </ul>
          <PaginationControls page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={setPage} />
        </>
      )}
    </section>
  );
}
