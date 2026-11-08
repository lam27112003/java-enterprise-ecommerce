import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrdersPage } from './OrdersPage';

const fetchOrders = vi.fn();

vi.mock('../api/ordersApi', () => ({
  fetchOrders: (...args: unknown[]) => fetchOrders(...args),
}));

describe('OrdersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lists orders and pay CTA for pending payment', async () => {
    fetchOrders.mockResolvedValue({
      content: [
        {
          id: 'o1',
          orderNumber: 'ORD-1',
          status: 'PENDING_PAYMENT',
          totalCents: 1000,
          currency: 'USD',
        },
        {
          id: 'o2',
          orderNumber: 'ORD-2',
          status: 'PAID',
          totalCents: 2000,
          currency: 'USD',
        },
      ],
      page: 0,
      size: 50,
      totalElements: 2,
      totalPages: 1,
    });

    render(
      <MemoryRouter>
        <OrdersPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('ORD-1')).toBeInTheDocument();
    expect(screen.getByText('ORD-2')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Pay now/i })).toHaveAttribute(
      'href',
      '/orders/o1/pay',
    );
  });

  it('shows empty state', async () => {
    fetchOrders.mockResolvedValue({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    });
    render(
      <MemoryRouter>
        <OrdersPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText(/No orders yet/i)).toBeInTheDocument();
  });
});
