import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import type { Category } from '../../../shared/types/catalog';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { createAdminCategory, fetchAdminCategories, fetchAdminCategory, updateAdminCategory } from '../api/categoriesApi';

const inputClass =
  'mt-1 w-full rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm text-brand-900';

export function AdminCategoryFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const [allCategories, setAllCategories] = useState<Category[]>([]);
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [description, setDescription] = useState('');
  const [parentId, setParentId] = useState('');
  const [active, setActive] = useState(true);
  const [sortOrder, setSortOrder] = useState('0');
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void fetchAdminCategories().then(setAllCategories).catch(() => setAllCategories([]));
  }, []);

  const load = useCallback(async () => {
    if (!isEdit || !id) return;
    setLoading(true);
    try {
      const c = await fetchAdminCategory(id);
      setName(c.name);
      setSlug(c.slug);
      setDescription(c.description ?? '');
      setParentId(c.parentId ?? '');
      setActive(c.active);
      setSortOrder(String(c.sortOrder));
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load category');
    } finally {
      setLoading(false);
    }
  }, [id, isEdit]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    const body = {
      name: name.trim(),
      slug: slug.trim() || null,
      description: description.trim() || null,
      parentId: parentId || null,
      active,
      sortOrder: Number(sortOrder) || 0,
    };
    try {
      if (isEdit && id) {
        await updateAdminCategory(id, body);
      } else {
        await createAdminCategory(body);
      }
      navigate('/admin/categories');
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading category…" />;
  }

  if (error && isEdit && !name) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="mx-auto max-w-xl space-y-6">
      <header>
        <p className="font-sans text-sm text-brand-600">
          <Link to="/admin/categories" className="underline">
            Categories
          </Link>
        </p>
        <h1 className="font-display text-3xl font-bold text-brand-900">
          {isEdit ? 'Edit category' : 'New category'}
        </h1>
      </header>

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-2xl border border-brand-100 bg-white/80 p-4">
        <div>
          <label htmlFor="cat-name" className="font-sans text-sm font-semibold text-brand-900">
            Name <span className="text-red-600" aria-hidden="true">*</span>
          </label>
          <input id="cat-name" value={name} onChange={(e) => setName(e.target.value)} className={inputClass} required />
        </div>
        <div>
          <label htmlFor="cat-slug" className="font-sans text-sm font-semibold text-brand-900">
            Slug
          </label>
          <input id="cat-slug" value={slug} onChange={(e) => setSlug(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label htmlFor="cat-desc" className="font-sans text-sm font-semibold text-brand-900">
            Description
          </label>
          <textarea id="cat-desc" value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className={inputClass} />
        </div>
        <div>
          <label htmlFor="cat-parent" className="font-sans text-sm font-semibold text-brand-900">
            Parent
          </label>
          <select id="cat-parent" value={parentId} onChange={(e) => setParentId(e.target.value)} className={inputClass}>
            <option value="">None</option>
            {allCategories.map((c) => (
              <option key={c.id} value={c.id} disabled={c.id === id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-wrap items-end gap-6">
          <label className="flex items-center gap-2 pb-2 font-sans text-sm text-brand-900">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            Active
          </label>
          <div>
            <label htmlFor="cat-sort" className="font-sans text-sm font-semibold text-brand-900">
              Sort order
            </label>
            <div className="mt-1 flex items-center gap-2">
              <button
                type="button"
                aria-label="Decrease sort order"
                className="rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm font-semibold text-brand-900 hover:bg-brand-50"
                onClick={() => setSortOrder(String(Math.max(0, (Number(sortOrder) || 0) - 1)))}
              >
                −
              </button>
              <input
                id="cat-sort"
                type="number"
                min={0}
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value)}
                className="w-24 rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm text-brand-900"
              />
              <button
                type="button"
                aria-label="Increase sort order"
                className="rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm font-semibold text-brand-900 hover:bg-brand-50"
                onClick={() => setSortOrder(String((Number(sortOrder) || 0) + 1))}
              >
                +
              </button>
            </div>
            <p className="mt-1 font-sans text-xs text-brand-600">Lower numbers appear first in category lists.</p>
          </div>
        </div>
        {error && (
          <p className="font-sans text-sm text-red-700" role="alert">
            {error}
          </p>
        )}
        <button type="submit" disabled={saving} className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white disabled:opacity-50">
          Save
        </button>
      </form>
    </section>
  );
}
