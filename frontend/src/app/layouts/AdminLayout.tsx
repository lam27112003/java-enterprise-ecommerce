import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';

const navClass = ({ isActive }: { isActive: boolean }) =>
  `block rounded-lg px-3 py-2 font-sans text-sm font-semibold ${
    isActive ? 'bg-brand-100 text-brand-900' : 'text-brand-700 hover:bg-brand-50'
  }`;

const NAV_ITEMS = [
  { to: '/admin', end: true, label: 'Dashboard' },
  { to: '/admin/products', label: 'Products' },
  { to: '/admin/categories', label: 'Categories' },
  { to: '/admin/inventory', label: 'Inventory' },
  { to: '/admin/promotions', label: 'Promotions' },
  { to: '/admin/orders', label: 'Orders' },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/refunds', label: 'Refunds' },
] as const;

export function AdminLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen bg-brand-50">
      <div className="mx-auto flex min-h-screen max-w-7xl">
        <aside className="hidden w-56 shrink-0 border-r border-brand-100 bg-white p-4 md:block">
          <Link to="/admin" className="font-display text-lg font-bold text-brand-900">
            Admin
          </Link>
          <p className="mt-1 truncate font-sans text-xs text-brand-500">{user?.email}</p>
          <nav className="mt-6 flex flex-col gap-1">
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} end={'end' in item ? item.end : false} className={navClass}>
                {item.label}
              </NavLink>
            ))}
            <button
              type="button"
              onClick={() => void logout()}
              className="mt-6 rounded-lg px-3 py-2 text-left font-sans text-sm font-semibold text-brand-700 hover:bg-brand-50"
            >
              Log out
            </button>
          </nav>
        </aside>
        <main className="flex-1 px-4 py-6 sm:px-6">
          <div className="mb-4 flex items-center justify-between md:hidden">
            <Link to="/admin" className="font-display text-lg font-bold text-brand-900">
              Admin
            </Link>
            <button
              type="button"
              onClick={() => void logout()}
              className="font-sans text-sm font-semibold text-brand-700"
            >
              Log out
            </button>
          </div>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
