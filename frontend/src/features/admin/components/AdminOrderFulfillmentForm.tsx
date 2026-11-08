import { useState } from 'react';
import type { Order, OrderStatus } from '../../../shared/types/order';
import {
  isUnpaidAdminTarget,
  nextFulfillmentStatus,
  requiresShippedTracking,
  validateShippedFields,
} from '../lib/orderTransitions';
import type { AdminStatusUpdateRequest } from '../types/adminTypes';

type Props = {
  order: Order;
  busy?: boolean;
  onSubmit: (body: AdminStatusUpdateRequest) => Promise<void>;
};

const inputClass =
  'mt-1 w-full rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm text-brand-900';

export function AdminOrderFulfillmentForm({ order, busy = false, onSubmit }: Props) {
  const next = nextFulfillmentStatus(order.status);
  const [target, setTarget] = useState<OrderStatus | ''>(next ?? '');
  const [trackingNumber, setTrackingNumber] = useState(order.trackingNumber ?? '');
  const [carrier, setCarrier] = useState(order.carrier ?? '');
  const [note, setNote] = useState('');
  const [validationError, setValidationError] = useState<string | null>(null);

  const unpaid = order.status === 'PENDING_PAYMENT';

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setValidationError(null);
    if (!target) return;

    if (requiresShippedTracking(order.status, target)) {
      const err = validateShippedFields(trackingNumber, carrier);
      if (err) {
        setValidationError(err);
        return;
      }
    }

    await onSubmit({
      status: target,
      trackingNumber: target === 'SHIPPED' ? trackingNumber.trim() : undefined,
      carrier: target === 'SHIPPED' ? carrier.trim() : undefined,
      note: note.trim() || undefined,
    });
  }

  if (!unpaid && !next) {
    return (
      <p className="font-sans text-sm text-brand-700">
        No admin status transitions available for {order.status}.
      </p>
    );
  }

  return (
    <form noValidate onSubmit={(e) => void handleSubmit(e)} className="space-y-4">
      <div>
        <label htmlFor="admin-order-target" className="font-sans text-sm font-semibold text-brand-900">
          New status
        </label>
        <select
          id="admin-order-target"
          value={target}
          onChange={(e) => setTarget(e.target.value as OrderStatus)}
          className={inputClass}
          disabled={busy}
        >
          {unpaid && (
            <>
              <option value="CANCELLED">CANCELLED</option>
              <option value="PAYMENT_FAILED">PAYMENT_FAILED</option>
            </>
          )}
          {!unpaid && next && <option value={next}>{next}</option>}
        </select>
      </div>

      {target === 'SHIPPED' && requiresShippedTracking(order.status, 'SHIPPED') && (
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="admin-tracking" className="font-sans text-sm font-semibold text-brand-900">
              Tracking number
            </label>
            <input
              id="admin-tracking"
              value={trackingNumber}
              onChange={(e) => setTrackingNumber(e.target.value)}
              className={inputClass}
              disabled={busy}
              required
            />
          </div>
          <div>
            <label htmlFor="admin-carrier" className="font-sans text-sm font-semibold text-brand-900">
              Carrier
            </label>
            <input
              id="admin-carrier"
              value={carrier}
              onChange={(e) => setCarrier(e.target.value)}
              className={inputClass}
              disabled={busy}
              required
            />
          </div>
        </div>
      )}

      <div>
        <label htmlFor="admin-order-note" className="font-sans text-sm font-semibold text-brand-900">
          Note (optional)
        </label>
        <textarea
          id="admin-order-note"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          rows={2}
          className={inputClass}
          disabled={busy}
        />
      </div>

      {validationError && (
        <p className="font-sans text-sm text-red-700" role="alert">
          {validationError}
        </p>
      )}

      <button
        type="submit"
        disabled={busy || !target || (unpaid && !isUnpaidAdminTarget(target as OrderStatus))}
        className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white disabled:opacity-50"
      >
        Update status
      </button>
    </form>
  );
}
