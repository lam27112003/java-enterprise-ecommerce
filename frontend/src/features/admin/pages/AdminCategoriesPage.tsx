import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import type { Category } from '../../../shared/types/catalog';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { deleteAdminCategory, fetchAdminCategories } from '../api/categoriesApi';

function CategoryRow({ category, depth }: { category: Category; depth: number }) {
  return (
    <>
      <li className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-brand-100 bg-white/80 px-4 py-2">
        <div style={{ paddingLeft: depth * 12 }}>
          <Link to={`/admin/categories/${category.id}/edit`} className="font-semibold text-brand-900 underline">
            {category.name}
          </Link>
          <p className="font-sans text-xs text-brand-600">
            {category.slug} · {category.active ? 'active' : 'inactive'}
          </p>
        </div>
      </li>
      {category.children?.map((child) => (
        <CategoryRow key={child.id} category={child} depth={depth + 1} />
      ))}
    </>
  );
}

export function AdminCategoriesPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setCategories(await fetchAdminCategories());
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load categories');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function onDelete(id: string) {
    if (!window.confirm('Delete category?')) return;
    try {
      await deleteAdminCategory(id);
      void load();
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Delete failed');
    }
  }

  if (loading) {
    return <LoadingState label="Loading categories…" />;
  }

  if (error) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-display text-3xl font-bold text-brand-900">Categories</h1>
        </div>
        <Link to="/admin/categories/new" className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white">
          New category
        </Link>
      </header>

      {categories.length === 0 ? (
        <EmptyState title="No categories" />
      ) : (
        <ul className="space-y-2">
          {categories.map((c) => (
            <div key={c.id}>
              <CategoryRow category={c} depth={0} />
              <button type="button" onClick={() => void onDelete(c.id)} className="ml-4 font-sans text-xs text-red-700 underline">
                Delete {c.name}
              </button>
            </div>
          ))}
        </ul>
      )}
    </section>
  );
}
