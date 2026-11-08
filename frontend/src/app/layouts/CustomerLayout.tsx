import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';

const navClass = ({ isActive }: { isActive: boolean }) =>
  `font-sans text-sm font-semibold ${isActive ? 'text-brand-900 underline' : 'text-brand-700 hover:text-brand-900'}`;

export function CustomerLayout() {
  const { isAuthenticated, user, isCustomer, logout } = useAuth();

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top,_#d9e8df,_#f3f7f4_45%,_#e8efe9)]">
      <header className="border-b border-brand-100/80 bg-white/70 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-4 sm:px-6">
          <Link to="/" className="font-display text-xl font-bold text-brand-900">
            Enterprise E-commerce
          </Link>
          <nav className="flex flex-wrap items-center gap-4">
            <NavLink to="/products" className={navClass}>
              Products
            </NavLink>
            {isCustomer && (
              <>
                <NavLink to="/cart" className={navClass}>
                  Cart
                </NavLink>
                <NavLink to="/orders" className={navClass}>
                  Orders
                </NavLink>
              </>
            )}
            {isAuthenticated ? (
              <>
                <NavLink to="/account" className={navClass}>
                  {user?.fullName ?? 'Account'}
                </NavLink>
                <button
                  type="button"
                  onClick={() => void logout()}
                  className="font-sans text-sm font-semibold text-brand-700 hover:text-brand-900"
                >
                  Log out
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login" className={navClass}>
                  Log in
                </NavLink>
                <NavLink
                  to="/register"
                  className="rounded-lg bg-brand-700 px-3 py-1.5 font-sans text-sm font-semibold text-white hover:bg-brand-900"
                >
                  Register
                </NavLink>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}
