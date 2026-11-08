import { useEffect, useState } from 'react';
import { fetchMe } from '../authApi';
import { isApiError } from '../../../shared/lib/errors';
import type { UserProfile } from '../../../shared/types/auth';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';

export function AccountPage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchMe()
      .then((data) => {
        if (!cancelled) {
          setProfile(data);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(isApiError(err) ? err.message : 'Failed to load profile');
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

  if (loading) {
    return <LoadingState label="Loading account…" />;
  }

  if (error || !profile) {
    return <ErrorState message={error ?? 'Profile unavailable'} />;
  }

  return (
    <section className="max-w-lg space-y-4">
      <h1 className="font-display text-3xl font-bold text-brand-900">Account</h1>
      <dl className="rounded-2xl border border-brand-100 bg-white/80 p-4 font-sans">
        <div className="py-2">
          <dt className="text-xs uppercase tracking-wide text-brand-500">Name</dt>
          <dd className="text-lg font-semibold text-brand-900">{profile.fullName}</dd>
        </div>
        <div className="py-2">
          <dt className="text-xs uppercase tracking-wide text-brand-500">Email</dt>
          <dd className="text-lg font-semibold text-brand-900">{profile.email}</dd>
        </div>
        <div className="py-2">
          <dt className="text-xs uppercase tracking-wide text-brand-500">Status</dt>
          <dd className="text-lg font-semibold text-brand-900">{profile.status}</dd>
        </div>
        <div className="py-2">
          <dt className="text-xs uppercase tracking-wide text-brand-500">Roles</dt>
          <dd className="text-lg font-semibold text-brand-900">{profile.roles.join(', ')}</dd>
        </div>
      </dl>
      <p className="font-sans text-sm text-brand-500">Address book is out of scope (P7-D3).</p>
    </section>
  );
}
