import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import type { Category, ProductSortField, ProductSummary, SortDirection } from '../../../shared/types/catalog';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { fetchCategories, fetchProducts, flattenCategories } from '../api/catalogApi';
import { CatalogToolbar } from '../components/CatalogToolbar';
import { PaginationControls } from '../components/PaginationControls';
import { ProductCard } from '../components/ProductCard';
import { ProductGridSkeleton } from '../components/ProductGridSkeleton';

const PAGE_SIZE = 12;

function parseSort(value: string | null): ProductSortField {
  const allowed: ProductSortField[] = ['name', 'priceCents', 'createdAt', 'sku'];
  return allowed.includes(value as ProductSortField) ? (value as ProductSortField) : 'createdAt';
}

function parseDirection(value: string | null): SortDirection {
  return value === 'asc' ? 'asc' : 'desc';
}

export function ProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const qParam = searchParams.get('q') ?? '';
  const categoryId = searchParams.get('categoryId') ?? '';
  const sort = parseSort(searchParams.get('sort'));
  const direction = parseDirection(searchParams.get('direction'));
  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0);

  const [draftQ, setDraftQ] = useState(qParam);
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setDraftQ(qParam);
  }, [qParam]);

  useEffect(() => {
    let cancelled = false;
    fetchCategories()
      .then((tree) => {
        if (!cancelled) {
          setCategories(tree);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCategories([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const loadProducts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const pageData = await fetchProducts({
        q: qParam || undefined,
        categoryId: categoryId || undefined,
        page,
        size: PAGE_SIZE,
        sort,
        direction,
      });
      setProducts(pageData.content);
      setTotalPages(pageData.totalPages);
      setTotalElements(pageData.totalElements);
    } catch (err) {
      setProducts([]);
      setError(isApiError(err) ? err.message : 'Failed to load products');
    } finally {
      setLoading(false);
    }
  }, [qParam, categoryId, page, sort, direction]);

  useEffect(() => {
    void loadProducts();
  }, [loadProducts]);

  const categoryNames = useMemo(() => {
    const map = new Map<string, string>();
    for (const category of flattenCategories(categories)) {
      map.set(category.id, category.name);
    }
    return map;
  }, [categories]);

  function updateParams(patch: Record<string, string | null>, resetPage = true) {
    const next = new URLSearchParams(searchParams);
    for (const [key, value] of Object.entries(patch)) {
      if (value == null || value === '') {
        next.delete(key);
      } else {
        next.set(key, value);
      }
    }
    if (resetPage) {
      next.delete('page');
    }
    setSearchParams(next);
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold text-brand-900">Products</h1>
        <p className="mt-2 font-sans text-brand-700">
          Browse the public catalog. Prices and availability are enforced by the backend.
        </p>
      </header>

      <CatalogToolbar
        q={draftQ}
        categoryId={categoryId}
        sort={sort}
        direction={direction}
        categories={categories}
        onSearchChange={setDraftQ}
        onCategoryChange={(value) => updateParams({ categoryId: value || null })}
        onSortChange={(nextSort, nextDirection) =>
          updateParams({ sort: nextSort, direction: nextDirection })
        }
        onSubmitSearch={() => updateParams({ q: draftQ.trim() || null })}
      />

      {loading && <ProductGridSkeleton />}
      {error && !loading && <ErrorState message={error} onRetry={() => void loadProducts()} />}
      {!loading && !error && products.length === 0 && (
        <EmptyState
          title="No products found"
          message="Try a different search term or clear the category filter."
        />
      )}
      {!loading && !error && products.length > 0 && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {products.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                categoryName={categoryNames.get(product.categoryId)}
              />
            ))}
          </div>
          <PaginationControls
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={(nextPage) =>
              updateParams({ page: nextPage === 0 ? null : String(nextPage) }, false)
            }
          />
        </>
      )}
    </section>
  );
}
