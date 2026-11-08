import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { isApiError, rateLimitOrApiMessage } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import {
  confirmExternalRefund,
  fetchAdminRefund,
  fetchRefundAudits,
  reopenAndSubmitRefund,
} from '../api/refundsApi';
import type { RefundAudit, RefundDetail } from '../types/adminTypes';

export function AdminRefundDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [detail, setDetail] = useState<RefundDetail | null>(null);
  const [audits, setAudits] = useState<RefundAudit[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmMessage, setConfirmMessage] = useState<string | null>(null);
  const [providerRef, setProviderRef] = useState('');
  const [confirmNote, setConfirmNote] = useState('');
  const [reopenNote, setReopenNote] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (silent = false) => {
    if (!id) {
      setError('Missing refund id');
      setLoading(false);
      return;
    }
    if (!silent) {
      setLoading(true);
    }
    setError(null);
    try {
      const d = await fetchAdminRefund(id);
      setDetail(d);
      setAudits(d.audits ?? []);
      try {
        setAudits(await fetchRefundAudits(id));
      } catch {
        /* detail may already include audits */
      }
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load refund');
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onConfirmExternal(e: { preventDefault: () => void }) {
    e.preventDefault();
    if (!id) return;
    setBusy(true);
    setActionError(null);
    setConfirmMessage(null);
    try {
      const result = await confirmExternalRefund(id, {
        providerRefundRef: providerRef.trim(),
        note: confirmNote.trim() || undefined,
      });
      if (result?.status) {
        setConfirmMessage(`External refund confirmed (${result.status}).`);
      }
      void load(true);
    } catch (err) {
      setActionError(rateLimitOrApiMessage(err, 'Confirm failed'));
    } finally {
      setBusy(false);
    }
  }

  async function onReopen() {
    if (!id) return;
    setBusy(true);
    setActionError(null);
    setConfirmMessage(null);
    try {
      const result = await reopenAndSubmitRefund(id, { note: reopenNote.trim() || undefined });
      if (result?.status) {
        setConfirmMessage(`Refund reopened (${result.status}).`);
      }
      void load(true);
    } catch (err) {
      setActionError(rateLimitOrApiMessage(err, 'Reopen failed'));
    } finally {
      setBusy(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading refund…" />;
  }

  if (error || !detail) {
    return <ErrorState message={error ?? 'Refund not found'} onRetry={() => void load()} />;
  }

  const { refund } = detail;

  return (
    <section className="space-y-6">
      <header>
        <p className="font-sans text-sm text-brand-600">
          <Link to="/admin/refunds" className="underline">
            Refunds
          </Link>
        </p>
        <h1 className="font-display text-3xl font-bold text-brand-900">Refund {refund.id.slice(0, 8)}…</h1>
        <p className="font-sans text-sm text-brand-700">
          {refund.status} · {formatMoney(refund.amountCents, refund.currency)}
        </p>
      </header>

      {actionError && (
        <p className="font-sans text-sm text-red-700" role="alert">
          {actionError}
        </p>
      )}
      {confirmMessage && (
        <p className="font-sans text-sm text-brand-800" role="status">
          {confirmMessage}
        </p>
      )}

      <div className="rounded-2xl border border-brand-100 bg-white/80 p-4 font-sans text-sm text-brand-800">
        <p>Order: {refund.orderId}</p>
        <p>Payment: {refund.paymentId}</p>
        <p>Provider ref: {refund.providerRefundRef ?? '—'}</p>
        <p>Payment amount: {formatMoney(detail.paymentAmountCents, detail.paymentCurrency)}</p>
      </div>

      {(refund.status === 'RECONCILIATION_REQUIRED' || refund.status === 'REFUND_SUBMITTED') && (
        <form onSubmit={(e) => void onConfirmExternal(e)} className="space-y-3 rounded-2xl border border-brand-100 bg-white/80 p-4">
          <h2 className="font-sans text-base font-semibold text-brand-900">Confirm external refund</h2>
          <input
            value={providerRef}
            onChange={(e) => setProviderRef(e.target.value)}
            placeholder="Provider refund reference"
            required
            className="w-full rounded-lg border border-brand-200 px-3 py-2 font-sans text-sm"
          />
          <textarea
            value={confirmNote}
            onChange={(e) => setConfirmNote(e.target.value)}
            placeholder="Note (optional)"
            rows={2}
            className="w-full rounded-lg border border-brand-200 px-3 py-2 font-sans text-sm"
          />
          <button
            type="submit"
            disabled={busy || !providerRef.trim()}
            className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white disabled:opacity-50"
          >
            Confirm external
          </button>
        </form>
      )}

      {refund.status === 'RECONCILIATION_REQUIRED' && (
        <div className="space-y-3 rounded-2xl border border-brand-100 bg-white/80 p-4">
          <h2 className="font-sans text-base font-semibold text-brand-900">Reopen and submit</h2>
          <textarea
            value={reopenNote}
            onChange={(e) => setReopenNote(e.target.value)}
            placeholder="Note (optional)"
            rows={2}
            className="w-full rounded-lg border border-brand-200 px-3 py-2 font-sans text-sm"
          />
          <button
            type="button"
            disabled={busy}
            onClick={() => void onReopen()}
            className="rounded-lg border border-brand-300 px-4 py-2 font-sans text-sm font-semibold text-brand-900 disabled:opacity-50"
          >
            Reopen and submit
          </button>
        </div>
      )}

      <div className="rounded-2xl border border-brand-100 bg-white/80 p-4">
        <h2 className="font-sans text-base font-semibold text-brand-900">Audit trail</h2>
        {audits.length === 0 ? (
          <p className="mt-2 font-sans text-sm text-brand-600">No audits.</p>
        ) : (
          <ul className="mt-2 space-y-2 font-sans text-sm text-brand-800">
            {audits.map((a) => (
              <li key={a.id}>
                {a.action}: {a.previousStatus ?? '—'} → {a.newStatus ?? '—'}
                {a.note ? ` · ${a.note}` : ''}
                <span className="text-brand-500"> · {new Date(a.createdAt).toLocaleString()}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
