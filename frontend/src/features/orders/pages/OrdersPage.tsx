import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import type { Order } from '../../../shared/types/order';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { fetchOrders } from '../api/ordersApi';

export function OrdersPage() {
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await fetchOrders(0, 50);
      setOrders(page.content);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load orders');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <LoadingState label="Loading orders…" />;
  }

  if (error) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold text-brand-900">Your orders</h1>
        <p className="mt-1 font-sans text-sm text-brand-700">
          Order and payment history from the backend. PAID appears only after webhook confirmation.
        </p>
      </header>

      {orders.length === 0 ? (
        <EmptyState title="No orders yet" message="When you checkout, orders will show up here." />
      ) : (
        <ul className="space-y-3">
          {orders.map((order) => (
            <li
              key={order.id}
              className="rounded-2xl border border-brand-100 bg-white/80 px-4 py-3 sm:flex sm:items-center sm:justify-between"
            >
              <div>
                <Link
                  to={`/orders/${order.id}`}
                  className="font-sans text-base font-semibold text-brand-900 underline"
                >
                  {order.orderNumber}
                </Link>
                <p className="mt-1 font-sans text-sm text-brand-600">
                  {order.status} · {formatMoney(order.totalCents, order.currency)}
                </p>
              </div>
              {order.status === 'PENDING_PAYMENT' && (
                <Link
                  to={`/orders/${order.id}/pay`}
                  className="mt-2 inline-block rounded-lg bg-brand-700 px-3 py-1.5 font-sans text-sm font-semibold text-white sm:mt-0"
                >
                  Pay now
                </Link>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
