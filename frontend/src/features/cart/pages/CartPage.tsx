import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import type { Cart } from '../../../shared/types/cart';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { clearCart, fetchCart, removeCartItem, updateCartItem } from '../api/cartApi';
import { CartItemRow } from '../components/CartItemRow';

export function CartPage() {
  const [cart, setCart] = useState<Cart | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchCart();
      setCart(data);
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

  async function onQuantityChange(itemId: string, quantity: number) {
    setBusy(true);
    setActionError(null);
    try {
      const next = await updateCartItem(itemId, { quantity });
      setCart(next);
    } catch (err) {
      setActionError(isApiError(err) ? err.message : 'Could not update quantity');
      await load();
    } finally {
      setBusy(false);
    }
  }

  async function onRemove(itemId: string) {
    setBusy(true);
    setActionError(null);
    try {
      const next = await removeCartItem(itemId);
      setCart(next);
    } catch (err) {
      setActionError(isApiError(err) ? err.message : 'Could not remove item');
    } finally {
      setBusy(false);
    }
  }

  async function onClear() {
    setBusy(true);
    setActionError(null);
    try {
      const next = await clearCart();
      setCart(next);
    } catch (err) {
      setActionError(isApiError(err) ? err.message : 'Could not clear cart');
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading cart…" />;
  }

  if (error) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  if (!cart || cart.items.length === 0) {
    return (
      <section className="space-y-4">
        <h1 className="font-display text-3xl font-bold text-brand-900">Your cart</h1>
        <EmptyState title="Cart is empty" message="Browse the catalog and add products to continue." />
        <Link to="/products" className="inline-block font-sans text-sm font-semibold text-brand-700 underline">
          Browse products
        </Link>
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-3xl font-bold text-brand-900">Your cart</h1>
          <p className="mt-1 font-sans text-sm text-brand-700">
            Line prices are snapshots from the cart API; checkout will re-price from catalog.
          </p>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={() => void onClear()}
          className="rounded-lg border border-brand-200 px-3 py-1.5 font-sans text-sm font-semibold text-brand-800 disabled:opacity-50"
        >
          Clear cart
        </button>
      </header>

      {actionError && (
        <p className="font-sans text-sm text-red-700" role="alert">
          {actionError}
        </p>
      )}

      <ul className="space-y-3">
        {cart.items.map((item) => (
          <CartItemRow
            key={item.id}
            item={item}
            busy={busy}
            onQuantityChange={(id, qty) => void onQuantityChange(id, qty)}
            onRemove={(id) => void onRemove(id)}
          />
        ))}
      </ul>

      <div className="rounded-2xl border border-brand-100 bg-white/80 p-4 sm:flex sm:items-center sm:justify-between">
        <p className="font-sans text-lg font-semibold text-brand-900">
          Subtotal {formatMoney(cart.subtotalCents, cart.currency)}
        </p>
        <Link
          to="/checkout"
          className="mt-3 inline-block rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white hover:bg-brand-900 sm:mt-0"
        >
          Checkout
        </Link>
      </div>
    </section>
  );
}
