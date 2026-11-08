import { Link } from 'react-router-dom';

export function UnauthorizedPage() {
  return (
    <section className="mx-auto max-w-lg py-16 text-center">
      <h1 className="font-display text-3xl font-bold text-brand-900">Access denied</h1>
      <p className="mt-3 font-sans text-brand-700">
        You do not have permission to view this page. Customer and admin areas are separated.
      </p>
      <Link
        to="/"
        className="mt-6 inline-block rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white"
      >
        Back home
      </Link>
    </section>
  );
}
