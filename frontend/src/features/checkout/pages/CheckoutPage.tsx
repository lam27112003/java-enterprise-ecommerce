import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fetchCart } from '../../cart/api/cartApi';
import { checkoutOrder } from '../../orders/api/ordersApi';
import { isApiError, rateLimitOrApiMessage } from '../../../shared/lib/errors';
import type { Cart } from '../../../shared/types/cart';
import type { PromotionPreview } from '../../../shared/types/promotion';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { previewCartPromo } from '../api/promoApi';
import { CheckoutSummary } from '../components/CheckoutSummary';
import { ShippingAddressForm } from '../components/ShippingAddressForm';
import {
  getOrCreateCheckoutIdempotencyKey,
  rotateCheckoutIdempotencyKey,
} from '../lib/idempotency';
import {
  EMPTY_SHIPPING,
  serializeShippingAddress,
  validateShippingAddress,
  type ShippingAddress,
} from '../lib/shippingAddress';

export function CheckoutPage() {
  const navigate = useNavigate();
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [shipping, setShipping] = useState<ShippingAddress>(EMPTY_SHIPPING);
  const [promoCode, setPromoCode] = useState('');
  const [preview, setPreview] = useState<PromotionPreview | null>(null);
  const [promoError, setPromoError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCart(await fetchCart());
    } catch (err) {
      setCart(null);
      setError(isApiError(err) ? err.message : 'Failed to load cart');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function onPreviewPromo() {
    setPromoError(null);
    setPreview(null);
    if (!promoCode.trim()) {
      return;
    }
    try {
      const result = await previewCartPromo(promoCode.trim());
      setPreview(result);
      if (!result.applicable) {
        setPromoError('Promotion is not applicable to this cart');
      }
    } catch (err) {
      setPromoError(isApiError(err) ? err.message : 'Could not preview promotion');
    }
  }

  async function onPlaceOrder() {
    setSubmitError(null);
    const validation = validateShippingAddress(shipping);
    if (validation) {
      setSubmitError(validation);
      return;
    }
    if (!cart || cart.items.length === 0) {
      setSubmitError('Cart is empty');
      return;
    }

    const shippingAddressJson = serializeShippingAddress(shipping);
    const promotionCode = promoCode.trim() ? promoCode.trim() : null;
    let idempotencyKey = getOrCreateCheckoutIdempotencyKey(promotionCode, shippingAddressJson);

    setSubmitting(true);
    try {
      const order = await checkoutOrder(
        { promotionCode, shippingAddressJson },
        idempotencyKey,
      );
      navigate(`/orders/${order.id}/pay`, { replace: true });
    } catch (err) {
      if (isApiError(err) && err.code === 'IDEMPOTENCY_KEY_CONFLICT') {
        idempotencyKey = rotateCheckoutIdempotencyKey(promotionCode, shippingAddressJson);
        setSubmitError('Checkout conflict with a previous payload. Try placing the order again.');
      } else if (isApiError(err)) {
        // Uncertain transport: keep same key for retry with unchanged payload.
        setSubmitError(rateLimitOrApiMessage(err, err.message));
      } else {
        setSubmitError('Checkout failed. If the network dropped, retry without changing the form.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading checkout…" />;
  }

  if (error) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  if (!cart || cart.items.length === 0) {
    return (
      <section className="space-y-4">
        <h1 className="font-display text-3xl font-bold text-brand-900">Checkout</h1>
        <EmptyState title="Nothing to checkout" message="Add products to your cart first." />
        <Link to="/products" className="inline-block font-sans text-sm font-semibold text-brand-700 underline">
          Browse products
        </Link>
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold text-brand-900">Checkout</h1>
        <p className="mt-1 font-sans text-sm text-brand-700">
          Shipping is sent as JSON to the checkout API. Prices are finalized by the server.
        </p>
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <div className="space-y-6 rounded-2xl border border-brand-100 bg-white/80 p-4 sm:p-6">
          <ShippingAddressForm value={shipping} disabled={submitting} onChange={setShipping} />

          <div className="space-y-2 border-t border-brand-100 pt-4">
            <label className="block font-sans text-sm font-semibold text-brand-900">
              Promotion code (optional)
              <div className="mt-1 flex flex-wrap gap-2">
                <input
                  className="min-w-[12rem] flex-1 rounded-lg border border-brand-200 px-3 py-2 font-sans text-sm"
                  value={promoCode}
                  disabled={submitting}
                  onChange={(e) => {
                    setPromoCode(e.target.value);
                    setPreview(null);
                  }}
                  placeholder="SAVE10"
                />
                <button
                  type="button"
                  disabled={submitting || !promoCode.trim()}
                  onClick={() => void onPreviewPromo()}
                  className="rounded-lg border border-brand-200 px-3 py-2 font-sans text-sm font-semibold text-brand-800 disabled:opacity-50"
                >
                  Preview
                </button>
              </div>
            </label>
            {promoError && (
              <p className="font-sans text-sm text-red-700" role="alert">
                {promoError}
              </p>
            )}
          </div>

          {submitError && (
            <p className="font-sans text-sm text-red-700" role="alert">
              {submitError}
            </p>
          )}

          <button
            type="button"
            disabled={submitting}
            onClick={() => void onPlaceOrder()}
            className="w-full rounded-lg bg-brand-700 px-4 py-3 font-sans text-sm font-semibold text-white hover:bg-brand-900 disabled:opacity-50 sm:w-auto"
          >
            {submitting ? 'Placing order…' : 'Place order & pay'}
          </button>
        </div>

        <CheckoutSummary cart={cart} preview={preview} />
      </div>
    </section>
  );
}
