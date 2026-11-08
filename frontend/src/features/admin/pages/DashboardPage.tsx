import { useCallback, useEffect, useState } from 'react';
import { isApiError } from '../../../shared/lib/errors';
import { formatMoney } from '../../../shared/lib/money';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { fetchDashboardSummary } from '../api/dashboardApi';
import type { DashboardSummary } from '../types/adminTypes';

function KpiCard({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-2xl border border-brand-100 bg-white/80 px-4 py-3">
      <p className="font-sans text-xs font-semibold uppercase tracking-wide text-brand-600">{label}</p>
      <p className="mt-1 font-display text-2xl font-bold text-brand-900">{value}</p>
    </div>
  );
}

function RevenueByCurrency({
  title,
  map,
}: {
  title: string;
  map: Record<string, number>;
}) {
  const entries = Object.entries(map ?? {});
  if (entries.length === 0) {
    return null;
  }
  return (
    <div className="rounded-2xl border border-brand-100 bg-white/80 px-4 py-3">
      <p className="font-sans text-sm font-semibold text-brand-900">{title}</p>
      <ul className="mt-2 space-y-1">
        {entries.map(([currency, cents]) => (
          <li key={currency} className="font-sans text-sm text-brand-800">
            {currency}: {formatMoney(cents, currency)}
          </li>
        ))}
      </ul>
    </div>
  );
}

export function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSummary(await fetchDashboardSummary());
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load dashboard');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return <LoadingState label="Loading dashboard…" />;
  }

  if (error || !summary) {
    return <ErrorState message={error ?? 'Dashboard unavailable'} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold text-brand-900">Dashboard</h1>
        <p className="mt-1 font-sans text-sm text-brand-700">
          Payment-period revenue ({summary.revenuePeriodDescription}): gross, refunded, and net totals
          below use successful payments recorded between{' '}
          {new Date(summary.revenuePeriodFrom).toLocaleString()} and{' '}
          {new Date(summary.revenuePeriodTo).toLocaleString()}.
        </p>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard label="Total products" value={summary.totalProducts} />
        <KpiCard label="Active products" value={summary.activeProducts} />
        <KpiCard label="Low stock" value={summary.lowStockProducts} />
        <KpiCard label="Open orders" value={summary.openOrders} />
        <KpiCard label="Awaiting fulfillment" value={summary.ordersAwaitingFulfillment} />
        <KpiCard label="Refunds to reconcile" value={summary.refundsRequiringReconciliation} />
        <KpiCard label="Orders in window" value={summary.ordersCreatedInWindow} />
      </div>

      <div className="grid gap-3 lg:grid-cols-3">
        <RevenueByCurrency title="Gross revenue (payment period)" map={summary.revenueGrossCentsByCurrency} />
        <RevenueByCurrency title="Refunded (payment period)" map={summary.revenueRefundedCentsByCurrency} />
        <RevenueByCurrency title="Net revenue (payment period)" map={summary.revenueNetCentsByCurrency} />
      </div>
    </section>
  );
}
