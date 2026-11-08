import { useCallback, useEffect, useState } from 'react';
import { isApiError } from '../../../shared/lib/errors';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { PaginationControls } from '../../catalog/components/PaginationControls';
import { adjustAdminInventory, fetchAdminInventory } from '../api/inventoryApi';
import type { InventoryRow } from '../types/adminTypes';

const PAGE_SIZE = 20;

export function AdminInventoryPage() {
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<InventoryRow[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [adjustProductId, setAdjustProductId] = useState<string | null>(null);
  const [delta, setDelta] = useState('');
  const [reason, setReason] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchAdminInventory(undefined, page, PAGE_SIZE);
      setRows(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load inventory');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function submitAdjust(productId: string) {
    setActionError(null);
    const parsedDelta = Number(delta);
    if (!reason.trim()) {
      setActionError('Reason is required.');
      return;
    }
    if (!Number.isFinite(parsedDelta) || parsedDelta === 0) {
      setActionError('Enter a non-zero delta.');
      return;
    }
    try {
      await adjustAdminInventory(productId, { delta: parsedDelta, reason: reason.trim() });
      setAdjustProductId(null);
      setDelta('');
      setReason('');
      void load();
    } catch (err) {
      setActionError(isApiError(err) ? err.message : 'Adjust failed');
    }
  }

  if (loading && rows.length === 0) {
    return <LoadingState label="Loading inventory…" />;
  }

  if (error && rows.length === 0) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold text-brand-900">Inventory</h1>
        <p className="mt-1 font-sans text-sm text-brand-700">Adjust available stock with delta and reason.</p>
      </header>

      {actionError && (
        <p className="font-sans text-sm text-red-700" role="alert">
          {actionError}
        </p>
      )}

      {rows.length === 0 ? (
        <EmptyState title="No inventory rows" />
      ) : (
        <>
          <ul className="space-y-3">
            {rows.map((row) => (
              <li key={row.productId} className="rounded-2xl border border-brand-100 bg-white/80 px-4 py-3">
                <p className="font-sans font-semibold text-brand-900">
                  {row.productName} ({row.sku})
                </p>
                <p className="font-sans text-sm text-brand-600">
                  Available {row.quantityAvailable} · Reserved {row.quantityReserved}
                </p>
                {adjustProductId === row.productId ? (
                  <div className="mt-3 space-y-2">
                    <input
                      type="number"
                      placeholder="Delta (+/-)"
                      value={delta}
                      onChange={(e) => setDelta(e.target.value)}
                      className="w-full rounded-lg border border-brand-200 px-3 py-2 font-sans text-sm"
                    />
                    <input
                      placeholder="Reason"
                      value={reason}
                      onChange={(e) => setReason(e.target.value)}
                      className="w-full rounded-lg border border-brand-200 px-3 py-2 font-sans text-sm"
                    />
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => void submitAdjust(row.productId)}
                        className="rounded-lg bg-brand-700 px-3 py-1.5 font-sans text-sm font-semibold text-white"
                      >
                        Apply
                      </button>
                      <button
                        type="button"
                        onClick={() => setAdjustProductId(null)}
                        className="font-sans text-sm text-brand-700 underline"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => setAdjustProductId(row.productId)}
                    className="mt-2 font-sans text-sm font-semibold text-brand-800 underline"
                  >
                    Adjust
                  </button>
                )}
              </li>
            ))}
          </ul>
          <PaginationControls page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={setPage} />
        </>
      )}
    </section>
  );
}
