import { FormEvent, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { isApiError } from '../../../shared/lib/errors';
import { isSafeInternalPath } from '../../../shared/lib/safePath';
import { isAdmin } from '../../../shared/types/auth';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const tokens = await login({ email: email.trim(), password });
      if (isSafeInternalPath(from)) {
        navigate(from, { replace: true });
      } else if (isAdmin(tokens.roles)) {
        navigate('/admin', { replace: true });
      } else {
        navigate('/', { replace: true });
      }
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Login failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <h1 className="font-display text-2xl font-bold text-brand-900">Log in</h1>
      <p className="mt-1 font-sans text-sm text-brand-700">Use your customer or admin account.</p>
      <form className="mt-6 space-y-4" onSubmit={(e) => void onSubmit(e)}>
        <label className="block font-sans text-sm">
          <span className="font-semibold text-brand-900">Email</span>
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="mt-1 w-full rounded-lg border border-brand-100 px-3 py-2 outline-none focus:border-brand-500"
          />
        </label>
        <label className="block font-sans text-sm">
          <span className="font-semibold text-brand-900">Password</span>
          <input
            type="password"
            required
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 w-full rounded-lg border border-brand-100 px-3 py-2 outline-none focus:border-brand-500"
          />
        </label>
        {error && (
          <p className="font-sans text-sm text-red-700" role="alert">
            {error}
          </p>
        )}
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-lg bg-brand-700 px-4 py-2.5 font-sans text-sm font-semibold text-white hover:bg-brand-900 disabled:opacity-60"
        >
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="mt-4 text-center font-sans text-sm text-brand-700">
        No account?{' '}
        <Link to="/register" className="font-semibold text-brand-900 underline">
          Register
        </Link>
      </p>
    </div>
  );
}
