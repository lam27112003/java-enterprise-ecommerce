import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminRoute } from './AdminRoute';
import { CustomerRoute } from './CustomerRoute';
import { clearTokens } from '../../../shared/lib/tokenStorage';

const authState = vi.hoisted(() => ({
  status: 'anonymous' as 'bootstrapping' | 'authenticated' | 'anonymous',
  isAuthenticated: false,
  isCustomer: false,
  isAdmin: false,
}));

vi.mock('../AuthContext', () => ({
  useAuth: () => ({
    status: authState.status,
    isAuthenticated: authState.isAuthenticated,
    isCustomer: authState.isCustomer,
    isAdmin: authState.isAdmin,
    user: null,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshSession: vi.fn(),
  }),
}));

function renderCustomerGuard() {
  return render(
    <MemoryRouter initialEntries={['/cart']}>
      <Routes>
        <Route path="/login" element={<div>Login page</div>} />
        <Route path="/admin" element={<div>Admin home</div>} />
        <Route path="/unauthorized" element={<div>Unauthorized</div>} />
        <Route element={<CustomerRoute />}>
          <Route path="/cart" element={<div>Cart page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

function renderAdminGuard() {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <Routes>
        <Route path="/login" element={<div>Login page</div>} />
        <Route path="/unauthorized" element={<div>Unauthorized</div>} />
        <Route path="/admin" element={<AdminRoute />}>
          <Route index element={<div>Admin dashboard</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('route guards', () => {
  beforeEach(() => {
    clearTokens();
    authState.status = 'anonymous';
    authState.isAuthenticated = false;
    authState.isCustomer = false;
    authState.isAdmin = false;
  });

  it('CustomerRoute redirects anonymous users to login', () => {
    renderCustomerGuard();
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });

  it('CustomerRoute allows CUSTOMER', () => {
    authState.status = 'authenticated';
    authState.isAuthenticated = true;
    authState.isCustomer = true;
    renderCustomerGuard();
    expect(screen.getByText('Cart page')).toBeInTheDocument();
  });

  it('CustomerRoute sends ADMIN-only users to admin', () => {
    authState.status = 'authenticated';
    authState.isAuthenticated = true;
    authState.isAdmin = true;
    authState.isCustomer = false;
    renderCustomerGuard();
    expect(screen.getByText('Admin home')).toBeInTheDocument();
  });

  it('AdminRoute rejects CUSTOMER', () => {
    authState.status = 'authenticated';
    authState.isAuthenticated = true;
    authState.isCustomer = true;
    authState.isAdmin = false;
    renderAdminGuard();
    expect(screen.getByText('Unauthorized')).toBeInTheDocument();
  });

  it('AdminRoute allows ADMIN', () => {
    authState.status = 'authenticated';
    authState.isAuthenticated = true;
    authState.isAdmin = true;
    renderAdminGuard();
    expect(screen.getByText('Admin dashboard')).toBeInTheDocument();
  });

  it('shows loading while bootstrapping', () => {
    authState.status = 'bootstrapping';
    renderAdminGuard();
    expect(screen.getByText('Checking session…')).toBeInTheDocument();
  });
});
