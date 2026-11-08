/** Runtime env for Phase 7 (P7-D6 / P7-D11). */

export function apiBase(): string {
  const raw = import.meta.env.VITE_API_BASE?.trim();
  return raw && raw.length > 0 ? raw.replace(/\/$/, '') : '/api/v1';
}

/** Stripe Test Mode publishable key. Empty until configured — Elements land in WP-C. */
export function stripePublishableKey(): string {
  return import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY?.trim() ?? '';
}

export function assertStripeTestPublishableKey(key: string): void {
  if (!key) {
    return;
  }
  if (key.startsWith('sk_')) {
    throw new Error('Stripe secret keys must never be used in the frontend');
  }
  if (key.startsWith('pk_live_')) {
    throw new Error('Stripe Live Mode publishable keys are forbidden in Phase 7');
  }
}
