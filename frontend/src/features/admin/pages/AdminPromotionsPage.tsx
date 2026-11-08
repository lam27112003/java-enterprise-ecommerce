import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { PaginationControls } from '../../catalog/components/PaginationControls';
import { deleteAdminPromotion, fetchAdminPromotions } from '../api/promotionsApi';
import type { Promotion } from '../types/adminTypes';

const PAGE_SIZE = 20;

export function AdminPromotionsPage() {
  const [page, setPage] = useState(0);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchAdminPromotions(page, PAGE_SIZE);
      setPromotions(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load promotions');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onDelete(id: string) {
    if (!window.confirm('Delete promotion?')) return;
    try {
      await deleteAdminPromotion(id);
      void load();
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Delete failed');
    }
  }

  if (loading && promotions.length === 0) {
    return <LoadingState label="Loading promotions…" />;
  }

  if (error && promotions.length === 0) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="font-display text-3xl font-bold text-brand-900">Promotions</h1>
        <Link to="/admin/promotions/new" className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white">
          New promotion
        </Link>
      </header>

      {promotions.length === 0 ? (
        <EmptyState title="No promotions" />
      ) : (
        <>
          <ul className="space-y-2">
            {promotions.map((p) => (
              <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 rounded-2xl border border-brand-100 bg-white/80 px-4 py-3">
                <div>
                  <Link to={`/admin/promotions/${p.id}/edit`} className="font-semibold text-brand-900 underline">
                    {p.code}
                  </Link>
                  <p className="font-sans text-sm text-brand-600">
                    {p.name} · {p.type} · {p.active ? 'active' : 'inactive'}
                  </p>
                </div>
                <button type="button" onClick={() => void onDelete(p.id)} className="text-sm text-red-700 underline">
                  Delete
                </button>
              </li>
            ))}
          </ul>
          <PaginationControls page={page} totalPages={totalPages} totalElements={totalElements} onPageChange={setPage} />
        </>
      )}
    </section>
  );
}
