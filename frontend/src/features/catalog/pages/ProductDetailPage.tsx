import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { addCartItem } from '../../cart/api/cartApi';
import { isApiError } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import type { ProductDetail } from '../../../shared/types/catalog';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { fetchProduct } from '../api/catalogApi';

const MIN_QTY = 1;
const MAX_QTY = 999;

export function ProductDetailPage() {
  const { idOrSlug = '' } = useParams();
  const navigate = useNavigate();
  const { isAuthenticated, isCustomer, isAdmin } = useAuth();

  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [qtyError, setQtyError] = useState<string | null>(null);
  const [cartMessage, setCartMessage] = useState<string | null>(null);
  const [cartError, setCartError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [selectedImageId, setSelectedImageId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    fetchProduct(idOrSlug)
      .then((data) => {
        if (cancelled) {
          return;
        }
        setProduct(data);
        const primary = data.images.find((image) => image.primary) ?? data.images[0];
        setSelectedImageId(primary?.id ?? null);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setProduct(null);
          setError(isApiError(err) ? err.message : 'Product could not be loaded');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [idOrSlug, reloadKey]);

  const activeImage = useMemo(() => {
    if (!product?.images.length) {
      return null;
    }
    return product.images.find((image) => image.id === selectedImageId) ?? product.images[0];
  }, [product, selectedImageId]);

  function validateQuantity(value: number): string | null {
    if (!Number.isInteger(value) || value < MIN_QTY || value > MAX_QTY) {
      return `Quantity must be an integer between ${MIN_QTY} and ${MAX_QTY}`;
    }
    return null;
  }

  async function onAddToCart(event: FormEvent) {
    event.preventDefault();
    setCartMessage(null);
    setCartError(null);

    if (!product) {
      return;
    }

    if (!isAuthenticated) {
      navigate('/login', { replace: false, state: { from: `/products/${idOrSlug}` } });
      return;
    }

    if (isAdmin && !isCustomer) {
      setCartError('Admin accounts cannot use the customer cart. Sign in with a CUSTOMER account.');
      return;
    }

    if (!isCustomer) {
      setCartError('A CUSTOMER account is required to add items to the cart.');
      return;
    }

    const invalid = validateQuantity(quantity);
    if (invalid) {
      setQtyError(invalid);
      return;
    }
    setQtyError(null);
    setSubmitting(true);
    try {
      await addCartItem({ productId: product.id, quantity });
      setCartMessage(`Added ${quantity} item(s) to your cart.`);
    } catch (err) {
      setCartError(isApiError(err) ? err.message : 'Could not add to cart');
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading product…" />;
  }

  if (error || !product) {
    return (
      <ErrorState
        title="Product unavailable"
        message={error ?? 'Not found'}
        onRetry={() => setReloadKey((value) => value + 1)}
      />
    );
  }

  return (
    <section className="grid gap-8 lg:grid-cols-2">
      <div>
        <div className="aspect-square overflow-hidden rounded-2xl border border-brand-100 bg-white/80">
          {activeImage ? (
            <img src={activeImage.url} alt={product.name} className="h-full w-full object-cover" />
          ) : (
            <div className="flex h-full items-center justify-center font-sans text-brand-500">No image</div>
          )}
        </div>
        {product.images.length > 1 && (
          <div className="mt-3 flex flex-wrap gap-2">
            {product.images.map((image) => (
              <button
                key={image.id}
                type="button"
                onClick={() => setSelectedImageId(image.id)}
                className={`h-16 w-16 overflow-hidden rounded-lg border ${
                  image.id === activeImage?.id ? 'border-brand-700' : 'border-brand-100'
                }`}
              >
                <img src={image.url} alt="" className="h-full w-full object-cover" />
              </button>
            ))}
          </div>
        )}
      </div>

      <div>
        <p className="font-sans text-sm font-semibold uppercase tracking-wide text-brand-500">
          <Link to={`/categories/${product.categorySlug}`} className="hover:underline">
            {product.categoryName}
          </Link>
        </p>
        <h1 className="mt-2 font-display text-4xl font-bold text-brand-900">{product.name}</h1>
        <p className="mt-2 font-sans text-sm text-brand-700">SKU {product.sku}</p>
        <p className="mt-4 font-sans text-2xl font-semibold text-brand-900">
          {formatMoney(product.priceCents, product.currency)}
        </p>
        <p className="mt-2 font-sans text-sm text-brand-700">
          Status: <span className="font-semibold">{product.status}</span>
          <span className="block text-brand-500">
            Exact stock quantity is not exposed on public APIs; the cart API rejects insufficient stock.
          </span>
        </p>
        {product.description && (
          <p className="mt-6 whitespace-pre-wrap font-sans text-brand-800">{product.description}</p>
        )}

        <form className="mt-8 space-y-4" onSubmit={(e) => void onAddToCart(e)}>
          <label className="block max-w-[8rem] font-sans text-sm">
            <span className="font-semibold text-brand-900">Quantity</span>
            <input
              type="number"
              min={MIN_QTY}
              max={MAX_QTY}
              step={1}
              value={quantity}
              onChange={(e) => {
                const next = Number(e.target.value);
                setQuantity(next);
                setQtyError(validateQuantity(next));
              }}
              className="mt-1 w-full rounded-lg border border-brand-100 px-3 py-2 outline-none focus:border-brand-500"
            />
          </label>
          {qtyError && (
            <p className="font-sans text-sm text-red-700" role="alert">
              {qtyError}
            </p>
          )}
          <div className="flex flex-wrap gap-3">
            <button
              type="submit"
              disabled={submitting || Boolean(qtyError)}
              className="rounded-lg bg-brand-700 px-5 py-2.5 font-sans text-sm font-semibold text-white hover:bg-brand-900 disabled:opacity-60"
            >
              {submitting ? 'Adding…' : 'Add to cart'}
            </button>
            <Link
              to="/cart"
              className="rounded-lg border border-brand-200 bg-white px-5 py-2.5 font-sans text-sm font-semibold text-brand-900"
            >
              View cart
            </Link>
          </div>
          {cartMessage && (
            <p className="font-sans text-sm text-brand-700" role="status">
              {cartMessage}
            </p>
          )}
          {cartError && (
            <p className="font-sans text-sm text-red-700" role="alert">
              {cartError}
            </p>
          )}
        </form>
      </div>
    </section>
  );
}
