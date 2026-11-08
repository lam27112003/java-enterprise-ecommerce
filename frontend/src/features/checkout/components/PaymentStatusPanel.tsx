import { Link } from 'react-router-dom';
import { formatMoney } from '../../../shared/lib/money';
import type { Order } from '../../../shared/types/order';
import type { Payment } from '../../../shared/types/payment';

type Props = {
  order: Order;
  payment: Payment | null;
  phase: 'idle' | 'processing' | 'paid' | 'terminal' | 'timeout';
};

export function PaymentStatusPanel({ order, payment, phase }: Props) {
  if (phase === 'paid' || order.status === 'PAID') {
    return (
      <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-4" role="status">
        <h2 className="font-sans text-base font-semibold text-emerald-900">Payment confirmed</h2>
        <p className="mt-1 font-sans text-sm text-emerald-800">
          Order {order.orderNumber} is <strong>PAID</strong> according to the backend (webhook
          authority). Total {formatMoney(order.totalCents, order.currency)}.
        </p>
        <Link
          to={`/orders/${order.id}`}
          className="mt-3 inline-block font-sans text-sm font-semibold text-emerald-900 underline"
        >
          View order
        </Link>
      </div>
    );
  }

  if (phase === 'terminal' || order.status === 'CANCELLED' || order.status === 'PAYMENT_FAILED') {
    return (
      <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-4" role="status">
        <h2 className="font-sans text-base font-semibold text-amber-900">Order not payable</h2>
        <p className="mt-1 font-sans text-sm text-amber-800">
          Status is {order.status}. Start a new checkout if you still want these items.
        </p>
        <Link to="/products" className="mt-3 inline-block font-sans text-sm font-semibold underline">
          Back to products
        </Link>
      </div>
    );
  }

  if (phase === 'processing' || phase === 'timeout') {
    return (
      <div className="rounded-xl border border-brand-200 bg-white/90 px-4 py-4" role="status">
        <h2 className="font-sans text-base font-semibold text-brand-900">
          {phase === 'timeout' ? 'Still confirming payment' : 'Payment processing…'}
        </h2>
        <p className="mt-1 font-sans text-sm text-brand-700">
          Stripe confirmation is not proof of PAID. Waiting for the verified backend webhook to
          update order status
          {payment ? ` (payment ${payment.status})` : ''}.
        </p>
        {phase === 'timeout' && (
          <p className="mt-2 font-sans text-sm text-brand-600">
            Refresh this page or check{' '}
            <Link to={`/orders/${order.id}`} className="underline">
              order details
            </Link>
            .
          </p>
        )}
      </div>
    );
  }

  return null;
}
