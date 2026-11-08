import { formatMoney } from '../../../shared/lib/money';
import type { Cart } from '../../../shared/types/cart';
import type { PromotionPreview } from '../../../shared/types/promotion';

type Props = {
  cart: Cart;
  preview: PromotionPreview | null;
};

export function CheckoutSummary({ cart, preview }: Props) {
  const estimateDiscount = preview?.applicable ? preview.discountCents : 0;
  const estimateTotal = preview?.applicable ? preview.totalCents : cart.subtotalCents;

  return (
    <aside className="rounded-2xl border border-brand-100 bg-white/80 p-4">
      <h2 className="font-display text-lg font-semibold text-brand-900">Order estimate</h2>
      <p className="mt-1 font-sans text-xs text-brand-500">
        Final totals come from the checkout API after you place the order. Cart line prices are
        re-priced from catalog on the server.
      </p>
      <ul className="mt-4 space-y-2">
        {cart.items.map((item) => (
          <li key={item.id} className="flex justify-between gap-3 font-sans text-sm text-brand-800">
            <span>
              {item.productName} × {item.quantity}
            </span>
            <span>{formatMoney(item.lineTotalCents, item.currency)}</span>
          </li>
        ))}
      </ul>
      <dl className="mt-4 space-y-1 border-t border-brand-100 pt-3 font-sans text-sm">
        <div className="flex justify-between text-brand-800">
          <dt>Subtotal</dt>
          <dd>{formatMoney(cart.subtotalCents, cart.currency)}</dd>
        </div>
        {estimateDiscount > 0 && (
          <div className="flex justify-between text-brand-800">
            <dt>Promo estimate ({preview?.code})</dt>
            <dd>−{formatMoney(estimateDiscount, cart.currency)}</dd>
          </div>
        )}
        <div className="flex justify-between font-semibold text-brand-900">
          <dt>Estimated total</dt>
          <dd>{formatMoney(estimateTotal, cart.currency)}</dd>
        </div>
      </dl>
    </aside>
  );
}
