import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import type { Order } from '../../../shared/types/order';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import {
  fetchAdminOrder,
  fetchAdminOrderStatusHistory,
  updateAdminOrderStatus,
} from '../api/ordersApi';
import { AdminOrderFulfillmentForm } from '../components/AdminOrderFulfillmentForm';
import type { AdminStatusUpdateRequest, OrderStatusHistoryEntry } from '../types/adminTypes';

export function AdminOrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<Order | null>(null);
  const [history, setHistory] = useState<OrderStatusHistoryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!id) {
      setError('Missing order id');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [o, h] = await Promise.all([fetchAdminOrder(id), fetchAdminOrderStatusHistory(id)]);
      setOrder(o);
      setHistory(h);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load order');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onStatusUpdate(body: AdminStatusUpdateRequest) {
    if (!id) return;
    setBusy(true);
    setActionError(null);
    try {
      const updated = await updateAdminOrderStatus(id, body);
      setOrder(updated);
      setHistory(await fetchAdminOrderStatusHistory(id));
    } catch (err) {
      setActionError(isApiError(err) ? err.message : 'Status update failed');
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading order…" />;
  }

  if (error || !order) {
    return <ErrorState message={error ?? 'Order not found'} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header className="space-y-1">
        <p className="font-sans text-sm text-brand-600">
          <Link to="/admin/orders" className="underline">
            Orders
          </Link>
        </p>
        <h1 className="font-display text-3xl font-bold text-brand-900">{order.orderNumber}</h1>
        <p className="font-sans text-sm text-brand-700">
          Status <strong>{order.status}</strong> · {formatMoney(order.totalCents, order.currency)}
        </p>
        {order.trackingNumber && (
          <p className="font-sans text-sm text-brand-700">
            Tracking {order.trackingNumber} ({order.carrier})
          </p>
        )}
      </header>

      {actionError && (
        <p className="font-sans text-sm text-red-700" role="alert">
          {actionError}
        </p>
      )}

      <div className="rounded-2xl border border-brand-100 bg-white/80 p-4">
        <h2 className="font-sans text-base font-semibold text-brand-900">Fulfillment</h2>
        <div className="mt-3">
          <AdminOrderFulfillmentForm order={order} busy={busy} onSubmit={onStatusUpdate} />
        </div>
      </div>

      <div className="rounded-2xl border border-brand-100 bg-white/80 p-4">
        <h2 className="font-sans text-base font-semibold text-brand-900">Line items</h2>
        <ul className="mt-2 space-y-1 font-sans text-sm text-brand-800">
          {order.items.map((item) => (
            <li key={item.id}>
              {item.productName} ({item.sku}) × {item.quantity} —{' '}
              {formatMoney(item.lineTotalCents, order.currency)}
            </li>
          ))}
        </ul>
      </div>

      <div className="rounded-2xl border border-brand-100 bg-white/80 p-4">
        <h2 className="font-sans text-base font-semibold text-brand-900">Status history</h2>
        {history.length === 0 ? (
          <p className="mt-2 font-sans text-sm text-brand-600">No history yet.</p>
        ) : (
          <ul className="mt-2 space-y-2 font-sans text-sm text-brand-800">
            {history.map((entry) => (
              <li key={entry.id}>
                {entry.previousStatus ?? '—'} → {entry.newStatus}
                {entry.note ? ` · ${entry.note}` : ''}
                <span className="text-brand-500"> · {new Date(entry.createdAt).toLocaleString()}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
