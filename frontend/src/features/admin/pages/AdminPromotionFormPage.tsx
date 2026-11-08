import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { isApiError } from '../../../shared/lib/errors';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { createAdminPromotion, fetchAdminPromotion, updateAdminPromotion } from '../api/promotionsApi';
import type { PromotionType } from '../types/adminTypes';

const inputClass =
  'mt-1 w-full rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm text-brand-900';

export function AdminPromotionFormPage() {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState<PromotionType>('PERCENT');
  const [valueCentsOrPercent, setValueCentsOrPercent] = useState('10');
  const [minOrderAmountCents, setMinOrderAmountCents] = useState('0');
  const [usageLimit, setUsageLimit] = useState('');
  const [active, setActive] = useState(true);
  const [loading, setLoading] = useState(isEdit);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!isEdit || !id) return;
    setLoading(true);
    try {
      const p = await fetchAdminPromotion(id);
      setCode(p.code);
      setName(p.name);
      setDescription(p.description ?? '');
      setType(p.type);
      setValueCentsOrPercent(String(p.valueCentsOrPercent));
      setMinOrderAmountCents(String(p.minOrderAmountCents));
      setUsageLimit(p.usageLimit != null ? String(p.usageLimit) : '');
      setActive(p.active);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load promotion');
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
      code: code.trim(),
      name: name.trim(),
      description: description.trim() || null,
      type,
      valueCentsOrPercent: Number(valueCentsOrPercent) || 0,
      minOrderAmountCents: Number(minOrderAmountCents) || 0,
      usageLimit: usageLimit ? Number(usageLimit) : null,
      active,
    };
    try {
      if (isEdit && id) {
        await updateAdminPromotion(id, body);
      } else {
        await createAdminPromotion(body);
      }
      navigate('/admin/promotions');
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading promotion…" />;
  }

  if (error && isEdit && !code) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="mx-auto max-w-xl space-y-6">
      <header>
        <p className="font-sans text-sm text-brand-600">
          <Link to="/admin/promotions" className="underline">
            Promotions
          </Link>
        </p>
        <h1 className="font-display text-3xl font-bold text-brand-900">
          {isEdit ? 'Edit promotion' : 'New promotion'}
        </h1>
      </header>

      <form onSubmit={(e) => void onSubmit(e)} className="space-y-4 rounded-2xl border border-brand-100 bg-white/80 p-4">
        <div>
          <label htmlFor="promo-code" className="font-sans text-sm font-semibold text-brand-900">
            Code
          </label>
          <input id="promo-code" value={code} onChange={(e) => setCode(e.target.value)} className={inputClass} required />
        </div>
        <div>
          <label htmlFor="promo-name" className="font-sans text-sm font-semibold text-brand-900">
            Name
          </label>
          <input id="promo-name" value={name} onChange={(e) => setName(e.target.value)} className={inputClass} required />
        </div>
        <div>
          <label htmlFor="promo-desc" className="font-sans text-sm font-semibold text-brand-900">
            Description
          </label>
          <textarea id="promo-desc" value={description} onChange={(e) => setDescription(e.target.value)} rows={2} className={inputClass} />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="promo-type" className="font-sans text-sm font-semibold text-brand-900">
              Type
            </label>
            <select id="promo-type" value={type} onChange={(e) => setType(e.target.value as PromotionType)} className={inputClass}>
              <option value="PERCENT">PERCENT</option>
              <option value="FIXED">FIXED</option>
            </select>
          </div>
          <div>
            <label htmlFor="promo-value" className="font-sans text-sm font-semibold text-brand-900">
              Value (cents or %)
            </label>
            <input id="promo-value" type="number" min={0} value={valueCentsOrPercent} onChange={(e) => setValueCentsOrPercent(e.target.value)} className={inputClass} required />
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div>
            <label htmlFor="promo-min" className="font-sans text-sm font-semibold text-brand-900">
              Min order (cents)
            </label>
            <input id="promo-min" type="number" min={0} value={minOrderAmountCents} onChange={(e) => setMinOrderAmountCents(e.target.value)} className={inputClass} />
          </div>
          <div>
            <label htmlFor="promo-limit" className="font-sans text-sm font-semibold text-brand-900">
              Usage limit (optional)
            </label>
            <input id="promo-limit" type="number" min={1} value={usageLimit} onChange={(e) => setUsageLimit(e.target.value)} className={inputClass} />
          </div>
        </div>
        <label className="flex items-center gap-2 font-sans text-sm text-brand-900">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          Active
        </label>
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
