import { useCallback, useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { createOrReusePayment, fetchOrderPayment } from '../../checkout/api/paymentApi';
import { pollUntilPaidOrTerminal } from '../../checkout/lib/paymentPolling';
import { parseShippingAddress } from '../../checkout/lib/shippingAddress';
import { isApiError } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import type { Order } from '../../../shared/types/order';
import type { Payment } from '../../../shared/types/payment';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { cancelOrder, fetchOrder } from '../api/ordersApi';

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const [order, setOrder] = useState<Order | null>(null);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [pollingNote, setPollingNote] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) {
      setError('Missing order id');
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const loaded = await fetchOrder(id);
      setOrder(loaded);
      try {
        setPayment(await fetchOrderPayment(id));
      } catch {
        setPayment(null);
      }
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load order');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!id || searchParams.get('payment') !== 'returning') {
      return;
    }
    let cancelled = false;
    const controller = new AbortController();
    setPollingNote('Confirming payment with the backend…');
    void (async () => {
      try {
        // Resume: obtain clientSecret only if still payable; polling uses GETs (no secret).
        try {
          await createOrReusePayment(id);
        } catch {
          /* may already be paid or not payable */
        }
        const result = await pollUntilPaidOrTerminal({
          orderId: id,
          signal: controller.signal,
          maxMs: 90_000,
        });
        if (cancelled) return;
        setOrder(result.order);
        setPayment(result.payment);
        setPollingNote(
          result.paid
            ? 'Payment confirmed by webhook.'
            : 'Still waiting for webhook confirmation — refresh later if needed.',
        );
      } catch {
        if (!cancelled) {
          setPollingNote('Could not confirm payment status yet.');
        }
      }
    })();
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [id, searchParams]);

  async function onCancel() {
    if (!id) return;
    setBusy(true);
    setActionError(null);
    try {
      const next = await cancelOrder(id);
      setOrder(next);
    } catch (err) {
      setActionError(isApiError(err) ? err.message : 'Could not cancel order');
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

  const shipping = parseShippingAddress(order.shippingAddressJson);

  return (
    <section className="space-y-6">
      <header className="space-y-1">
        <p className="font-sans text-sm text-brand-600">
          <Link to="/orders" className="underline">
            All orders
          </Link>
        </p>
        <h1 className="font-display text-3xl font-bold text-brand-900">{order.orderNumber}</h1>
        <p className="font-sans text-sm text-brand-700">
          Status <strong>{order.status}</strong> · {formatMoney(order.totalCents, order.currency)}
        </p>
      </header>

      {pollingNote && (
        <p className="rounded-lg border border-brand-100 bg-white/80 px-3 py-2 font-sans text-sm text-brand-800">
          {pollingNote}
        </p>
      )}

      {actionError && (
        <p className="font-sans text-sm text-red-700" role="alert">
          {actionError}
        </p>
      )}

      <div className="flex flex-wrap gap-3">
        {order.status === 'PENDING_PAYMENT' && (
          <>
            <Link
              to={`/orders/${order.id}/pay`}
              className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white"
            >
              Resume payment
            </Link>
            <button
              type="button"
              disabled={busy}
              onClick={() => void onCancel()}
              className="rounded-lg border border-brand-200 px-4 py-2 font-sans text-sm font-semibold text-brand-800 disabled:opacity-50"
            >
              Cancel unpaid order
            </button>
          </>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-2xl border border-brand-100 bg-white/80 p-4">
          <h2 className="font-display text-lg font-semibold text-brand-900">Totals</h2>
          <dl className="mt-3 space-y-1 font-sans text-sm text-brand-800">
            <div className="flex justify-between">
              <dt>Subtotal</dt>
              <dd>{formatMoney(order.subtotalCents, order.currency)}</dd>
            </div>
            <div className="flex justify-between">
              <dt>Discount{order.promotionCode ? ` (${order.promotionCode})` : ''}</dt>
              <dd>−{formatMoney(order.discountCents, order.currency)}</dd>
            </div>
            <div className="flex justify-between">
              <dt>Tax</dt>
              <dd>{formatMoney(order.taxCents, order.currency)}</dd>
            </div>
            <div className="flex justify-between font-semibold text-brand-900">
              <dt>Total</dt>
              <dd>{formatMoney(order.totalCents, order.currency)}</dd>
            </div>
          </dl>
        </div>

        <div className="rounded-2xl border border-brand-100 bg-white/80 p-4">
          <h2 className="font-display text-lg font-semibold text-brand-900">Payment</h2>
          {payment ? (
            <dl className="mt-3 space-y-1 font-sans text-sm text-brand-800">
              <div className="flex justify-between gap-2">
                <dt>Status</dt>
                <dd>{payment.status}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt>Amount</dt>
                <dd>{formatMoney(payment.amountCents, payment.currency)}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt>Provider ref</dt>
                <dd className="truncate">{payment.providerRef ?? '—'}</dd>
              </div>
            </dl>
          ) : (
            <p className="mt-3 font-sans text-sm text-brand-600">No payment attempt yet.</p>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-brand-100 bg-white/80 p-4">
        <h2 className="font-display text-lg font-semibold text-brand-900">Items</h2>
        <ul className="mt-3 space-y-2">
          {order.items.map((item) => (
            <li key={item.id} className="flex justify-between gap-3 font-sans text-sm text-brand-800">
              <span>
                {item.productName} ({item.sku}) × {item.quantity}
              </span>
              <span>{formatMoney(item.lineTotalCents, order.currency)}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="rounded-2xl border border-brand-100 bg-white/80 p-4">
        <h2 className="font-display text-lg font-semibold text-brand-900">Shipping</h2>
        {shipping ? (
          <address className="mt-3 font-sans text-sm not-italic text-brand-800">
            {shipping.fullName}
            <br />
            {shipping.line1}
            {shipping.line2 ? (
              <>
                <br />
                {shipping.line2}
              </>
            ) : null}
            <br />
            {shipping.city}, {shipping.region} {shipping.postalCode}
            <br />
            {shipping.country}
          </address>
        ) : (
          <p className="mt-3 font-sans text-sm text-brand-600">
            {order.shippingAddressJson || 'No shipping address on file.'}
          </p>
        )}
        {(order.trackingNumber || order.carrier) && (
          <p className="mt-3 font-sans text-sm text-brand-700">
            Tracking: {order.carrier ?? '—'} {order.trackingNumber ?? ''}
          </p>
        )}
      </div>
    </section>
  );
}
