import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import type { ProductSummary } from '../../../shared/types/catalog';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { PaginationControls } from '../../catalog/components/PaginationControls';
import { deleteAdminProduct, fetchAdminProducts } from '../api/productsApi';

const PAGE_SIZE = 20;

export function AdminProductsPage() {
  const [q, setQ] = useState('');
  const [draftQ, setDraftQ] = useState('');
  const [page, setPage] = useState(0);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchAdminProducts({ q: q || undefined, page, size: PAGE_SIZE });
      setProducts(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load products');
    } finally {
      setLoading(false);
    }
  }, [q, page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onDelete(id: string) {
    if (!window.confirm('Delete this product?')) return;
    try {
      await deleteAdminProduct(id);
      void load();
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Delete failed');
    }
  }

  if (loading && products.length === 0) {
    return <LoadingState label="Loading products…" />;
  }

  if (error && products.length === 0) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-3xl font-bold text-brand-900">Products</h1>
          <p className="mt-1 font-sans text-sm text-brand-700">Catalog management.</p>
        </div>
        <Link
          to="/admin/products/new"
          className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white"
        >
          New product
        </Link>
      </header>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setPage(0);
          setQ(draftQ.trim());
        }}
        className="flex gap-2"
      >
        <input
          value={draftQ}
          onChange={(e) => setDraftQ(e.target.value)}
          placeholder="Search SKU or name"
          className="flex-1 rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm"
        />
        <button type="submit" className="rounded-lg border border-brand-200 bg-white px-4 py-2 font-sans text-sm font-semibold">
          Search
        </button>
      </form>

      {products.length === 0 ? (
        <EmptyState title="No products" />
      ) : (
        <>
          <ul className="space-y-2">
            {products.map((p) => (
              <li
                key={p.id}
                className="flex flex-wrap items-center justify-between gap-2 rounded-2xl border border-brand-100 bg-white/80 px-4 py-3"
              >
                <div>
                  <Link to={`/admin/products/${p.id}/edit`} className="font-semibold text-brand-900 underline">
                    {p.name}
                  </Link>
                  <p className="font-sans text-sm text-brand-600">
                    {p.sku} · {p.status} · {formatMoney(p.priceCents, p.currency)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => void onDelete(p.id)}
                  className="font-sans text-sm text-red-700 underline"
                >
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
