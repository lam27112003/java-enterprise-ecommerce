import { Elements } from '@stripe/react-stripe-js';
import { loadStripe, type Stripe } from '@stripe/stripe-js';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchOrder } from '../../orders/api/ordersApi';
import { assertStripeTestPublishableKey, stripePublishableKey } from '../../../shared/lib/env';
import { isApiError, rateLimitOrApiMessage } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import type { Order } from '../../../shared/types/order';
import type { PayResponse, Payment } from '../../../shared/types/payment';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { createOrReusePayment, fetchOrderPayment } from '../api/paymentApi';
import { PaymentStatusPanel } from '../components/PaymentStatusPanel';
import { StripePaymentForm } from '../components/StripePaymentForm';
import { pollUntilPaidOrTerminal } from '../lib/paymentPolling';

let stripePromise: Promise<Stripe | null> | null = null;

function getStripe(): Promise<Stripe | null> {
  if (!stripePromise) {
    const key = stripePublishableKey();
    assertStripeTestPublishableKey(key);
    if (!key) {
      stripePromise = Promise.resolve(null);
    } else {
      stripePromise = loadStripe(key);
    }
  }
  return stripePromise;
}

type Phase = 'idle' | 'processing' | 'paid' | 'terminal' | 'timeout';

export function PayOrderPage() {
  const { id } = useParams<{ id: string }>();
  const [order, setOrder] = useState<Order | null>(null);
  const [pay, setPay] = useState<PayResponse | null>(null);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>('idle');
  const [stripeReady, setStripeReady] = useState<Stripe | null | undefined>(undefined);
  const abortRef = useRef<AbortController | null>(null);

  const bootstrap = useCallback(async () => {
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
      if (loaded.status === 'PAID') {
        setPhase('paid');
        try {
          setPayment(await fetchOrderPayment(id));
        } catch {
          /* optional */
        }
        return;
      }
      if (loaded.status !== 'PENDING_PAYMENT') {
        setPhase('terminal');
        return;
      }
      const payResponse = await createOrReusePayment(id);
      // clientSecret kept in React state only — never sessionStorage/localStorage.
      setPay(payResponse);
      setPhase('idle');
    } catch (err) {
      setError(rateLimitOrApiMessage(err, 'Failed to prepare payment'));
      setPay(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void bootstrap();
    void getStripe().then(setStripeReady);
    return () => {
      abortRef.current?.abort();
    };
  }, [bootstrap]);

  async function startPolling() {
    if (!id) return;
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setPhase('processing');
    try {
      const result = await pollUntilPaidOrTerminal({
        orderId: id,
        signal: controller.signal,
        onTick: (tick) => {
          setOrder(tick.order);
          setPayment(tick.payment);
        },
      });
      setOrder(result.order);
      setPayment(result.payment);
      if (result.paid) {
        setPhase('paid');
      } else if (result.terminalUnpaid) {
        setPhase('terminal');
      } else {
        setPhase('timeout');
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        return;
      }
      setConfirmError(isApiError(err) ? err.message : 'Could not refresh payment status');
      setPhase('timeout');
    }
  }

  if (loading || stripeReady === undefined) {
    return <LoadingState label="Preparing payment…" />;
  }

  if (error) {
    return <ErrorState message={error} onRetry={() => void bootstrap()} />;
  }

  if (!order) {
    return <ErrorState message="Order not found" />;
  }

  const showElements =
    order.status === 'PENDING_PAYMENT' &&
    phase !== 'paid' &&
    phase !== 'terminal' &&
    pay?.clientSecret &&
    stripeReady;

  return (
    <section className="mx-auto max-w-xl space-y-6">
      <header>
        <p className="font-sans text-sm text-brand-600">
          <Link to={`/orders/${order.id}`} className="underline">
            Order {order.orderNumber}
          </Link>
        </p>
        <h1 className="font-display text-3xl font-bold text-brand-900">Pay</h1>
        <p className="mt-1 font-sans text-sm text-brand-700">
          Order total {formatMoney(order.totalCents, order.currency)} · status {order.status}
        </p>
      </header>

      <PaymentStatusPanel order={order} payment={payment} phase={phase} />

      {order.status === 'PENDING_PAYMENT' && phase === 'idle' && (
        <p className="font-sans text-sm text-brand-600">
          After you confirm with Stripe, this page polls the backend. Only a verified webhook marks
          the order PAID.
        </p>
      )}

      {confirmError && (
        <p className="font-sans text-sm text-red-700" role="alert">
          {confirmError}
        </p>
      )}

      {!stripePublishableKey() && order.status === 'PENDING_PAYMENT' && (
        <ErrorState
          title="Stripe not configured"
          message="Set VITE_STRIPE_PUBLISHABLE_KEY to a pk_test_… key."
        />
      )}

      {showElements && (
        <div className="rounded-2xl border border-brand-100 bg-white/90 p-4 sm:p-6">
          <Elements
            stripe={stripeReady}
            options={{
              clientSecret: pay.clientSecret,
              appearance: { theme: 'stripe' },
            }}
          >
            <StripePaymentForm
              orderId={order.id}
              amountCents={pay.amountCents}
              currency={pay.currency}
              onError={setConfirmError}
              onConfirmed={() => void startPolling()}
            />
          </Elements>
        </div>
      )}

      {(phase === 'timeout' || phase === 'processing') && (
        <button
          type="button"
          className="font-sans text-sm font-semibold text-brand-700 underline"
          onClick={() => void startPolling()}
        >
          Refresh payment status
        </button>
      )}
    </section>
  );
}
