import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { RegisterPage } from '../features/auth/pages/RegisterPage';
import { AdminRoute } from '../features/auth/components/AdminRoute';
import { CustomerRoute } from '../features/auth/components/CustomerRoute';

const authState = vi.hoisted(() => ({
  status: 'anonymous' as 'bootstrapping' | 'authenticated' | 'anonymous',
  isAuthenticated: false,
  isCustomer: false,
  isAdmin: false,
}));

vi.mock('../features/auth/AuthContext', () => ({
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

describe('router smoke (React Router 7)', () => {
  it('renders login and register pages', () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();

    render(
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByRole('heading', { name: 'Create account' })).toBeInTheDocument();
  });

  it('redirects unauthenticated customer route to login', () => {
    authState.status = 'anonymous';
    authState.isAuthenticated = false;
    render(
      <MemoryRouter initialEntries={['/cart']}>
        <Routes>
          <Route path="/login" element={<div>Login page</div>} />
          <Route element={<CustomerRoute />}>
            <Route path="/cart" element={<div>Cart page</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('Login page')).toBeInTheDocument();
  });

  it('blocks customer from admin route', () => {
    authState.status = 'authenticated';
    authState.isAuthenticated = true;
    authState.isCustomer = true;
    authState.isAdmin = false;
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/unauthorized" element={<div>Unauthorized</div>} />
          <Route path="/admin" element={<AdminRoute />}>
            <Route index element={<div>Admin dashboard</div>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );
    expect(screen.getByText('Unauthorized')).toBeInTheDocument();
  });
});
