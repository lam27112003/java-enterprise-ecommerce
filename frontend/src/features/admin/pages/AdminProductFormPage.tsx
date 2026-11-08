import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import type { Category, ProductStatus } from '../../../shared/types/catalog';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { fetchAdminCategories } from '../api/categoriesApi';
import { createAdminProduct, fetchAdminProduct, updateAdminProduct } from '../api/productsApi';
import type { ProductRequest } from '../types/adminTypes';

const inputClass =
  'mt-1 w-full rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm text-brand-900';

export function AdminProductFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const [categories, setCategories] = useState<Category[]>([]);
  const [categoryId, setCategoryId] = useState('');
  const [sku, setSku] = useState('');
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [description, setDescription] = useState('');
  const [priceCents, setPriceCents] = useState('0');
  const [currency, setCurrency] = useState('USD');
  const [status, setStatus] = useState<ProductStatus>('DRAFT');
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadCategories = useCallback(async () => {
    try {
      const list = await fetchAdminCategories();
      setCategories(list);
      if (!categoryId && list[0]) setCategoryId(list[0].id);
    } catch {
      setCategories([]);
    }
  }, [categoryId]);

  const loadProduct = useCallback(async () => {
    if (!isEdit || !id) return;
    setLoading(true);
    setError(null);
    try {
      const p = await fetchAdminProduct(id);
      setCategoryId(p.categoryId);
      setSku(p.sku);
      setName(p.name);
      setSlug(p.slug);
      setDescription(p.description ?? '');
      setPriceCents(String(p.priceCents));
      setCurrency(p.currency);
      setStatus(p.status);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load product');
    } finally {
      setLoading(false);
    }
  }, [id, isEdit]);

  useEffect(() => {
    void loadCategories();
  }, [loadCategories]);

  useEffect(() => {
    void loadProduct();
  }, [loadProduct]);

  function buildBody(): ProductRequest {
    return {
      categoryId,
      sku: sku.trim(),
      name: name.trim(),
      slug: slug.trim() || null,
      description: description.trim() || null,
      priceCents: Number(priceCents) || 0,
      currency: currency.trim() || 'USD',
      status,
    };
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const body = buildBody();
      if (isEdit && id) {
        await updateAdminProduct(id, body);
      } else {
        await createAdminProduct(body);
      }
      navigate('/admin/products');
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading product…" />;
  }

  if (error && isEdit && !name) {
    return <ErrorState message={error} onRetry={() => void loadProduct()} />;
  }

  return (
    <section className="mx-auto max-w-xl space-y-6">
      <header>
        <p className="font-sans text-sm text-brand-600">
          <Link to="/admin/products" className="underline">
            Products
          </Link>
        </p>
        <h1 className="font-display text-3xl font-bold text-brand-900">
          {isEdit ? 'Edit product' : 'New product'}
        </h1>
      </header>

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-2xl border border-brand-100 bg-white/80 p-4">
        <div>
          <label htmlFor="prod-category" className="font-sans text-sm font-semibold text-brand-900">
            Category
          </label>
          <select id="prod-category" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className={inputClass} required>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="prod-sku" className="font-sans text-sm font-semibold text-brand-900">
            SKU
          </label>
          <input id="prod-sku" value={sku} onChange={(e) => setSku(e.target.value)} className={inputClass} required />
        </div>
        <div>
          <label htmlFor="prod-name" className="font-sans text-sm font-semibold text-brand-900">
            Name
          </label>
          <input id="prod-name" value={name} onChange={(e) => setName(e.target.value)} className={inputClass} required />
        </div>
        <div>
          <label htmlFor="prod-slug" className="font-sans text-sm font-semibold text-brand-900">
            Slug (optional)
          </label>
          <input id="prod-slug" value={slug} onChange={(e) => setSlug(e.target.value)} className={inputClass} />
        </div>
        <div>
          <label htmlFor="prod-desc" className="font-sans text-sm font-semibold text-brand-900">
            Description
          </label>
          <textarea id="prod-desc" value={description} onChange={(e) => setDescription(e.target.value)} rows={3} className={inputClass} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="prod-price" className="font-sans text-sm font-semibold text-brand-900">
              Price (cents)
            </label>
            <input id="prod-price" type="number" min={0} value={priceCents} onChange={(e) => setPriceCents(e.target.value)} className={inputClass} required />
          </div>
          <div>
            <label htmlFor="prod-currency" className="font-sans text-sm font-semibold text-brand-900">
              Currency
            </label>
            <input id="prod-currency" maxLength={3} value={currency} onChange={(e) => setCurrency(e.target.value)} className={inputClass} required />
          </div>
        </div>
        <div>
          <label htmlFor="prod-status" className="font-sans text-sm font-semibold text-brand-900">
            Status
          </label>
          <select id="prod-status" value={status} onChange={(e) => setStatus(e.target.value as ProductStatus)} className={inputClass}>
            <option value="DRAFT">DRAFT</option>
            <option value="ACTIVE">ACTIVE</option>
            <option value="ARCHIVED">ARCHIVED</option>
          </select>
        </div>
        {error && (
          <p className="font-sans text-sm text-red-700" role="alert">
            {error}
          </p>
        )}
        <button type="submit" disabled={saving} className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white disabled:opacity-50">
          {saving ? 'Saving…' : 'Save'}
        </button>
      </form>
    </section>
  );
}
