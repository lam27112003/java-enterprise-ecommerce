import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../shared/lib/errors';
import { CartPage } from './CartPage';

vi.mock('../api/cartApi', () => ({
  fetchCart: vi.fn(),
  updateCartItem: vi.fn(),
  removeCartItem: vi.fn(),
  clearCart: vi.fn(),
}));

import { clearCart, fetchCart, removeCartItem, updateCartItem } from '../api/cartApi';

const fetchCartMock = vi.mocked(fetchCart);
const updateCartItemMock = vi.mocked(updateCartItem);
const removeCartItemMock = vi.mocked(removeCartItem);
const clearCartMock = vi.mocked(clearCart);

const sampleCart = {
  id: 'cart1',
  userId: 'u1',
  currency: 'USD',
  subtotalCents: 2598,
  updatedAt: '',
  items: [
    {
      id: 'item1',
      productId: 'p1',
      productName: 'Ceramic Mug',
      sku: 'SKU-1',
      quantity: 2,
      unitPriceCents: 1299,
      currency: 'USD',
      lineTotalCents: 2598,
      available: true,
    },
  ],
};

function renderCart() {
  return render(
    <MemoryRouter>
      <CartPage />
    </MemoryRouter>,
  );
}

describe('CartPage', () => {
  beforeEach(() => {
    fetchCartMock.mockResolvedValue(sampleCart);
    updateCartItemMock.mockResolvedValue({
      ...sampleCart,
      items: [{ ...sampleCart.items[0], quantity: 3, lineTotalCents: 3897 }],
      subtotalCents: 3897,
    });
    removeCartItemMock.mockResolvedValue({
      ...sampleCart,
      items: [],
      subtotalCents: 0,
    });
    clearCartMock.mockResolvedValue({ ...sampleCart, items: [], subtotalCents: 0 });
  });

  it('renders cart items and subtotal', async () => {
    renderCart();
    expect(await screen.findByText('Ceramic Mug')).toBeInTheDocument();
    expect(screen.getByText(/Subtotal/)).toBeInTheDocument();
  });

  it('shows empty cart state', async () => {
    fetchCartMock.mockResolvedValue({ ...sampleCart, items: [], subtotalCents: 0 });
    renderCart();
    expect(await screen.findByText('Cart is empty')).toBeInTheDocument();
  });

  it('updates quantity via cart API', async () => {
    renderCart();
    await screen.findByText('Ceramic Mug');
    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '3' } });
    await waitFor(() => {
      expect(updateCartItemMock).toHaveBeenCalledWith('item1', { quantity: 3 });
    });
  });

  it('removes cart items', async () => {
    renderCart();
    await screen.findByText('Ceramic Mug');
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    await waitFor(() => {
      expect(removeCartItemMock).toHaveBeenCalledWith('item1');
    });
    expect(await screen.findByText('Cart is empty')).toBeInTheDocument();
  });

  it('surfaces cart API errors', async () => {
    fetchCartMock.mockRejectedValue(new ApiError(500, 'SERVER', 'Cart failed'));
    renderCart();
    expect(await screen.findByText('Cart failed')).toBeInTheDocument();
  });

  it('surfaces insufficient stock on quantity update', async () => {
    updateCartItemMock.mockRejectedValue(
      new ApiError(409, 'INSUFFICIENT_STOCK', 'Requested quantity exceeds available inventory'),
    );
    renderCart();
    await screen.findByText('Ceramic Mug');
    fireEvent.change(screen.getByLabelText('Quantity'), { target: { value: '50' } });
    expect(
      await screen.findByText('Requested quantity exceeds available inventory'),
    ).toBeInTheDocument();
  });
});
