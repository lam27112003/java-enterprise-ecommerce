import { PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import { useState, type FormEvent } from 'react';
import { formatMoney } from '../../../shared/lib/money';

type Props = {
  orderId: string;
  amountCents: number;
  currency: string;
  onConfirmed: () => void;
  onError: (message: string) => void;
};

export function StripePaymentForm({ orderId, amountCents, currency, onConfirmed, onError }: Props) {
  const stripe = useStripe();
  const elements = useElements();
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!stripe || !elements) {
      return;
    }
    setSubmitting(true);
    onError('');
    try {
      const returnUrl = `${window.location.origin}/orders/${orderId}?payment=returning`;
      const result = await stripe.confirmPayment({
        elements,
        redirect: 'if_required',
        confirmParams: {
          return_url: returnUrl,
        },
      });

      if (result.error) {
        onError(result.error.message ?? 'Payment confirmation failed');
        return;
      }

      // confirmPayment success is NOT proof the order is PAID — parent must poll backend.
      onConfirmed();
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Payment confirmation failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={(e) => void onSubmit(e)} className="space-y-4">
      <p className="font-sans text-sm text-brand-700">
        Amount charged by Stripe:{' '}
        <span className="font-semibold text-brand-900">{formatMoney(amountCents, currency)}</span>
        <span className="block text-xs text-brand-500">
          Sourced from the pay API response. Order is marked PAID only after the verified webhook.
        </span>
      </p>
      <PaymentElement />
      <button
        type="submit"
        disabled={!stripe || !elements || submitting}
        className="w-full rounded-lg bg-brand-700 px-4 py-3 font-sans text-sm font-semibold text-white hover:bg-brand-900 disabled:opacity-50"
      >
        {submitting ? 'Confirming…' : 'Pay now'}
      </button>
    </form>
  );
}
