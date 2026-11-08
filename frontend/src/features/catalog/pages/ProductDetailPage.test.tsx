import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../shared/lib/errors';
import { ProductDetailPage } from './ProductDetailPage';

const authState = vi.hoisted(() => ({
  isAuthenticated: true,
  isCustomer: true,
  isAdmin: false,
}));

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({
    status: 'authenticated',
    isAuthenticated: authState.isAuthenticated,
    isCustomer: authState.isCustomer,
    isAdmin: authState.isAdmin,
    user: { userId: 'u1', email: 'c@example.com', fullName: 'Cust', roles: ['CUSTOMER'] },
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    refreshSession: vi.fn(),
  }),
}));

vi.mock('../api/catalogApi', () => ({
  fetchProduct: vi.fn(),
}));

vi.mock('../../cart/api/cartApi', () => ({
  addCartItem: vi.fn(),
}));

import { fetchProduct } from '../api/catalogApi';
import { addCartItem } from '../../cart/api/cartApi';

const fetchProductMock = vi.mocked(fetchProduct);
const addCartItemMock = vi.mocked(addCartItem);

const product = {
  id: 'p1',
  categoryId: 'c1',
  categoryName: 'Drinkware',
  categorySlug: 'drinkware',
  sku: 'SKU-1',
  name: 'Ceramic Mug',
  slug: 'ceramic-mug',
  description: 'A nice mug',
  priceCents: 1299,
  currency: 'USD',
  status: 'ACTIVE' as const,
  createdAt: '',
  updatedAt: '',
  images: [],
};

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/products/ceramic-mug']}>
      <Routes>
        <Route path="/products/:idOrSlug" element={<ProductDetailPage />} />
        <Route path="/login" element={<div>Login page</div>} />
        <Route path="/cart" element={<div>Cart page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProductDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authState.isAuthenticated = true;
    authState.isCustomer = true;
    authState.isAdmin = false;
    fetchProductMock.mockResolvedValue(product);
    addCartItemMock.mockResolvedValue({
      id: 'cart1',
      userId: 'u1',
      items: [],
      subtotalCents: 0,
      currency: 'USD',
      updatedAt: '',
    });
  });

  it('renders product detail fields', async () => {
    renderDetail();
    expect(await screen.findByRole('heading', { name: 'Ceramic Mug' })).toBeInTheDocument();
    expect(screen.getByText('A nice mug')).toBeInTheDocument();
    expect(screen.getByText(/Drinkware/)).toBeInTheDocument();
  });

  it('validates invalid quantities', async () => {
    renderDetail();
    await screen.findByRole('heading', { name: 'Ceramic Mug' });
    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '0' } });
    expect(screen.getByText(/Quantity must be an integer/)).toBeInTheDocument();
  });

  it('adds to cart for CUSTOMER', async () => {
    renderDetail();
    await screen.findByRole('heading', { name: 'Ceramic Mug' });
    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add to cart' }));
    await waitFor(() => {
      expect(addCartItemMock).toHaveBeenCalledWith({ productId: 'p1', quantity: 2 });
    });
    expect(await screen.findByText(/Added 2 item/)).toBeInTheDocument();
  });

  it('shows stock errors from cart API', async () => {
    addCartItemMock.mockRejectedValue(
      new ApiError(409, 'INSUFFICIENT_STOCK', 'Requested quantity exceeds available inventory'),
    );
    renderDetail();
    await screen.findByRole('heading', { name: 'Ceramic Mug' });
    fireEvent.click(screen.getByRole('button', { name: 'Add to cart' }));
    expect(
      await screen.findByText('Requested quantity exceeds available inventory'),
    ).toBeInTheDocument();
  });

  it('blocks ADMIN from add-to-cart', async () => {
    authState.isAdmin = true;
    authState.isCustomer = false;
    renderDetail();
    await screen.findByRole('heading', { name: 'Ceramic Mug' });
    fireEvent.click(screen.getByRole('button', { name: 'Add to cart' }));
    expect(await screen.findByText(/Admin accounts cannot use the customer cart/)).toBeInTheDocument();
    expect(addCartItemMock).not.toHaveBeenCalled();
  });

  it('redirects unauthenticated users to login', async () => {
    authState.isAuthenticated = false;
    authState.isCustomer = false;
    renderDetail();
    await screen.findByRole('heading', { name: 'Ceramic Mug' });
    fireEvent.click(screen.getByRole('button', { name: 'Add to cart' }));
    expect(await screen.findByText('Login page')).toBeInTheDocument();
  });
});
