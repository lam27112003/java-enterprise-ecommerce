import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import type { Order, OrderStatus } from '../../../shared/types/order';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { PaginationControls } from '../../catalog/components/PaginationControls';
import { fetchAdminOrders } from '../api/ordersApi';

const PAGE_SIZE = 20;
const STATUSES: (OrderStatus | '')[] = [
  '',
  'PENDING_PAYMENT',
  'PAID',
  'PROCESSING',
  'SHIPPED',
  'DELIVERED',
  'CANCELLED',
  'PAYMENT_FAILED',
];

export function AdminOrdersPage() {
  const [statusFilter, setStatusFilter] = useState<OrderStatus | ''>('');
  const [page, setPage] = useState(0);
  const [orders, setOrders] = useState<Order[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchAdminOrders(statusFilter || undefined, page, PAGE_SIZE);
      setOrders(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load orders');
    } finally {
      setLoading(false);
    }
  }, [statusFilter, page]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading && orders.length === 0) {
    return <LoadingState label="Loading orders…" />;
  }

  if (error && orders.length === 0) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold text-brand-900">Orders</h1>
      </header>

      <div>
        <label htmlFor="order-status-filter" className="font-sans text-sm font-semibold text-brand-900">
          Filter by status
        </label>
        <select
          id="order-status-filter"
          value={statusFilter}
          onChange={(e) => {
            setPage(0);
            setStatusFilter(e.target.value as OrderStatus | '');
          }}
          className="mt-1 rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm"
        >
          {STATUSES.map((s) => (
            <option key={s || 'all'} value={s}>
              {s || 'All'}
            </option>
          ))}
        </select>
      </div>

      {orders.length === 0 ? (
        <EmptyState title="No orders" />
      ) : (
        <>
          <ul className="space-y-2">
            {orders.map((order) => (
              <li key={order.id} className="rounded-2xl border border-brand-100 bg-white/80 px-4 py-3">
                <Link to={`/admin/orders/${order.id}`} className="font-semibold text-brand-900 underline">
                  {order.orderNumber}
                </Link>
                <p className="font-sans text-sm text-brand-600">
                  {order.status} · {formatMoney(order.totalCents, order.currency)}
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
