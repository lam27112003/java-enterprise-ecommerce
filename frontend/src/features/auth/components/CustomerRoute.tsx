import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { LoadingState } from '../../../shared/ui/LoadingState';

/**
 * Requires an authenticated CUSTOMER. ADMIN users are redirected to /admin
 * (P7-D2: admins do not use customer commerce routes).
 */
export function CustomerRoute() {
  const { status, isAuthenticated, isCustomer, isAdmin } = useAuth();
  const location = useLocation();

  if (status === 'bootstrapping') {
    return <LoadingState label="Checking session…" />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (isAdmin && !isCustomer) {
    return <Navigate to="/admin" replace />;
  }

  if (!isCustomer) {
    return <Navigate to="/unauthorized" replace />;
  }

  return <Outlet />;
}
