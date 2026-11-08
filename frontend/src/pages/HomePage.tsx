import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiRequest } from '../shared/lib/apiClient';
import { ErrorState } from '../shared/ui/ErrorState';
import { LoadingState } from '../shared/ui/LoadingState';

type PlatformInfo = {
  name: string;
  environment: string;
  phase: string;
  status: string;
};

export function HomePage() {
  const [info, setInfo] = useState<PlatformInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    apiRequest<PlatformInfo>('/platform/info', { skipAuth: true, skipRefresh: true })
      .then((data) => {
        if (!cancelled) {
          setInfo(data);
        }
      })
      .catch((err: Error) => {
        if (!cancelled) {
          setError(err.message);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <section>
      <p className="font-sans text-sm font-semibold uppercase tracking-[0.2em] text-brand-500">
        Phase 7 — storefront foundation
      </p>
      <h1 className="mt-4 font-display text-4xl font-bold text-brand-900 md:text-5xl">
        Enterprise E-commerce
      </h1>
      <p className="mt-4 max-w-2xl font-sans text-lg text-brand-700">
        Browse the catalog, open product details, and manage your cart as a CUSTOMER. Checkout and
        Stripe payment arrive in WP-C.
      </p>
      <div className="mt-6 flex flex-wrap gap-3">
        <Link
          to="/products"
          className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white hover:bg-brand-900"
        >
          Browse products
        </Link>
      </div>

      <div className="mt-10 rounded-2xl border border-brand-100 bg-white/70 p-6 shadow-sm backdrop-blur">
        <h2 className="font-sans text-sm font-semibold uppercase tracking-wide text-brand-500">
          Platform status
        </h2>
        {loading && <LoadingState label="Loading platform info…" />}
        {error && !loading && (
          <div className="mt-3">
            <ErrorState
              title="Backend not reachable"
              message={`${error}. Start Compose infra and the API, then refresh.`}
            />
          </div>
        )}
        {info && (
          <dl className="mt-4 grid gap-3 font-sans sm:grid-cols-2">
            <div>
              <dt className="text-xs uppercase tracking-wide text-brand-500">Name</dt>
              <dd className="text-lg font-semibold">{info.name}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-brand-500">Environment</dt>
              <dd className="text-lg font-semibold">{info.environment}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-brand-500">Phase</dt>
              <dd className="text-lg font-semibold">{info.phase}</dd>
            </div>
            <div>
              <dt className="text-xs uppercase tracking-wide text-brand-500">Status</dt>
              <dd className="text-lg font-semibold">{info.status}</dd>
            </div>
          </dl>
        )}
      </div>
    </section>
  );
}
