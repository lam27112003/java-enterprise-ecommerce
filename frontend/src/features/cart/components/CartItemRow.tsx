import { formatMoney } from '../../../shared/lib/money';
import type { CartItem } from '../../../shared/types/cart';

type Props = {
  item: CartItem;
  busy: boolean;
  onQuantityChange: (itemId: string, quantity: number) => void;
  onRemove: (itemId: string) => void;
};

export function CartItemRow({ item, busy, onQuantityChange, onRemove }: Props) {
  return (
    <li className="grid gap-3 rounded-2xl border border-brand-100 bg-white/80 p-4 sm:grid-cols-[1fr_auto] sm:items-center">
      <div>
        <p className="font-display text-lg font-bold text-brand-900">{item.productName}</p>
        <p className="font-sans text-sm text-brand-700">SKU {item.sku}</p>
        <p className="mt-1 font-sans text-sm text-brand-700">
          {formatMoney(item.unitPriceCents, item.currency)} each
        </p>
        {!item.available && (
          <p className="mt-2 font-sans text-sm font-semibold text-red-700" role="status">
            Currently unavailable
          </p>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <label className="font-sans text-sm">
          <span className="sr-only">Quantity</span>
          <input
            type="number"
            min={1}
            max={999}
            value={item.quantity}
            disabled={busy}
            onChange={(e) => {
              const next = Number(e.target.value);
              if (Number.isInteger(next) && next >= 1 && next <= 999) {
                onQuantityChange(item.id, next);
              }
            }}
            className="w-20 rounded-lg border border-brand-100 px-2 py-1.5"
          />
        </label>
        <p className="min-w-[5rem] font-sans text-sm font-semibold text-brand-900">
          {formatMoney(item.lineTotalCents, item.currency)}
        </p>
        <button
          type="button"
          disabled={busy}
          onClick={() => onRemove(item.id)}
          className="rounded-lg border border-red-200 px-3 py-1.5 font-sans text-sm font-semibold text-red-800 hover:bg-red-50 disabled:opacity-50"
        >
          Remove
        </button>
      </div>
    </li>
  );
}
