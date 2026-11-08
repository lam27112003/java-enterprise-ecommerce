import { Link } from 'react-router-dom';
import { formatMoney } from '../../../shared/lib/money';
import type { ProductSummary } from '../../../shared/types/catalog';

type Props = {
  product: ProductSummary;
  categoryName?: string;
};

export function ProductCard({ product, categoryName }: Props) {
  const href = `/products/${product.slug || product.id}`;

  return (
    <article className="flex flex-col overflow-hidden rounded-2xl border border-brand-100 bg-white/80 shadow-sm">
      <Link to={href} className="block aspect-[4/3] bg-brand-50">
        {product.primaryImageUrl ? (
          <img
            src={product.primaryImageUrl}
            alt={product.name}
            className="h-full w-full object-cover"
            loading="lazy"
          />
        ) : (
          <div className="flex h-full items-center justify-center font-sans text-sm text-brand-500">
            No image
          </div>
        )}
      </Link>
      <div className="flex flex-1 flex-col gap-2 p-4">
        {categoryName && (
          <p className="font-sans text-xs font-semibold uppercase tracking-wide text-brand-500">
            {categoryName}
          </p>
        )}
        <Link to={href} className="font-display text-lg font-bold text-brand-900 hover:underline">
          {product.name}
        </Link>
        <p className="font-sans text-sm text-brand-700">{product.sku}</p>
        <div className="mt-auto flex items-center justify-between gap-2 pt-2">
          <p className="font-sans text-base font-semibold text-brand-900">
            {formatMoney(product.priceCents, product.currency)}
          </p>
          <span className="rounded-full bg-brand-50 px-2 py-0.5 font-sans text-xs font-semibold text-brand-700">
            {product.status}
          </span>
        </div>
      </div>
    </article>
  );
}
