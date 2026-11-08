import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import type { Category, ProductSummary } from '../../../shared/types/catalog';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { fetchCategory, fetchProducts } from '../api/catalogApi';
import { PaginationControls } from '../components/PaginationControls';
import { ProductCard } from '../components/ProductCard';
import { ProductGridSkeleton } from '../components/ProductGridSkeleton';

const PAGE_SIZE = 12;

export function CategoryPage() {
  const { slug = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(0, Number(searchParams.get('page') ?? '0') || 0);

  const [category, setCategory] = useState<Category | null>(null);
  const [products, setProducts] = useState<ProductSummary[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    async function load() {
      try {
        const cat = await fetchCategory(slug);
        if (cancelled) {
          return;
        }
        setCategory(cat);
        const pageData = await fetchProducts({
          categoryId: cat.id,
          page,
          size: PAGE_SIZE,
          sort: 'createdAt',
          direction: 'desc',
        });
        if (cancelled) {
          return;
        }
        setProducts(pageData.content);
        setTotalPages(pageData.totalPages);
        setTotalElements(pageData.totalElements);
      } catch (err) {
        if (!cancelled) {
          setCategory(null);
          setProducts([]);
          setError(isApiError(err) ? err.message : 'Category could not be loaded');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, [slug, page]);

  if (loading && !category) {
    return <LoadingState label="Loading category…" />;
  }

  if (error && !category) {
    return <ErrorState message={error} />;
  }

  return (
    <section className="space-y-6">
      <header>
        <p className="font-sans text-sm text-brand-500">
          <Link to="/products" className="hover:underline">
            Products
          </Link>
        </p>
        <h1 className="mt-2 font-display text-3xl font-bold text-brand-900">
          {category?.name ?? 'Category'}
        </h1>
        {category?.description && (
          <p className="mt-2 max-w-2xl font-sans text-brand-700">{category.description}</p>
        )}
      </header>

      {loading && <ProductGridSkeleton />}
      {!loading && error && <ErrorState message={error} />}
      {!loading && !error && products.length === 0 && (
        <EmptyState title="No products in this category" message="Check back later or browse all products." />
      )}
      {!loading && !error && products.length > 0 && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {products.map((product) => (
              <ProductCard key={product.id} product={product} categoryName={category?.name} />
            ))}
          </div>
          <PaginationControls
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={(nextPage) => {
              const next = new URLSearchParams(searchParams);
              if (nextPage === 0) {
                next.delete('page');
              } else {
                next.set('page', String(nextPage));
              }
              setSearchParams(next);
            }}
          />
        </>
      )}
    </section>
  );
}
