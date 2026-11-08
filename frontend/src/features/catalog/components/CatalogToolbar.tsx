import type { Category, ProductSortField, SortDirection } from '../../../shared/types/catalog';
import { flattenCategories } from '../api/catalogApi';

type Props = {
  q: string;
  categoryId: string;
  sort: ProductSortField;
  direction: SortDirection;
  categories: Category[];
  onSearchChange: (value: string) => void;
  onCategoryChange: (value: string) => void;
  onSortChange: (sort: ProductSortField, direction: SortDirection) => void;
  onSubmitSearch: () => void;
};

const SORT_OPTIONS: Array<{ value: string; sort: ProductSortField; direction: SortDirection; label: string }> = [
  { value: 'createdAt:desc', sort: 'createdAt', direction: 'desc', label: 'Newest' },
  { value: 'createdAt:asc', sort: 'createdAt', direction: 'asc', label: 'Oldest' },
  { value: 'name:asc', sort: 'name', direction: 'asc', label: 'Name A–Z' },
  { value: 'name:desc', sort: 'name', direction: 'desc', label: 'Name Z–A' },
  { value: 'priceCents:asc', sort: 'priceCents', direction: 'asc', label: 'Price ↑' },
  { value: 'priceCents:desc', sort: 'priceCents', direction: 'desc', label: 'Price ↓' },
  { value: 'sku:asc', sort: 'sku', direction: 'asc', label: 'SKU A–Z' },
];

export function CatalogToolbar({
  q,
  categoryId,
  sort,
  direction,
  categories,
  onSearchChange,
  onCategoryChange,
  onSortChange,
  onSubmitSearch,
}: Props) {
  const flat = flattenCategories(categories);
  const sortValue = `${sort}:${direction}`;

  return (
    <form
      className="flex flex-col gap-3 rounded-2xl border border-brand-100 bg-white/70 p-4 sm:flex-row sm:flex-wrap sm:items-end"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmitSearch();
      }}
    >
      <label className="min-w-[12rem] flex-1 font-sans text-sm">
        <span className="font-semibold text-brand-900">Search</span>
        <input
          type="search"
          value={q}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Name or SKU"
          className="mt-1 w-full rounded-lg border border-brand-100 px-3 py-2 outline-none focus:border-brand-500"
        />
      </label>
      <label className="min-w-[10rem] font-sans text-sm">
        <span className="font-semibold text-brand-900">Category</span>
        <select
          value={categoryId}
          onChange={(e) => onCategoryChange(e.target.value)}
          className="mt-1 w-full rounded-lg border border-brand-100 px-3 py-2 outline-none focus:border-brand-500"
        >
          <option value="">All categories</option>
          {flat.map((category) => (
            <option key={category.id} value={category.id}>
              {'—'.repeat(category.depth)} {category.name}
            </option>
          ))}
        </select>
      </label>
      <label className="min-w-[10rem] font-sans text-sm">
        <span className="font-semibold text-brand-900">Sort</span>
        <select
          value={sortValue}
          onChange={(e) => {
            const option = SORT_OPTIONS.find((item) => item.value === e.target.value);
            if (option) {
              onSortChange(option.sort, option.direction);
            }
          }}
          className="mt-1 w-full rounded-lg border border-brand-100 px-3 py-2 outline-none focus:border-brand-500"
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
      <button
        type="submit"
        className="rounded-lg bg-brand-700 px-4 py-2.5 font-sans text-sm font-semibold text-white hover:bg-brand-900"
      >
        Apply
      </button>
    </form>
  );
}
