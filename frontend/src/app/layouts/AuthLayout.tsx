import { Link, Outlet } from 'react-router-dom';

export function AuthLayout() {
  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top,_#d9e8df,_#f3f7f4_45%,_#e8efe9)]">
      <div className="mx-auto flex min-h-screen max-w-md flex-col justify-center px-4 py-12">
        <Link to="/" className="mb-8 text-center font-display text-2xl font-bold text-brand-900">
          Enterprise E-commerce
        </Link>
        <div className="rounded-2xl border border-brand-100 bg-white/80 p-6 shadow-sm backdrop-blur">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
