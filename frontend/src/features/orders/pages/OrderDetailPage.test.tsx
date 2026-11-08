import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrderDetailPage } from './OrderDetailPage';

const fetchOrder = vi.fn();
const cancelOrder = vi.fn();
const fetchOrderPayment = vi.fn();

vi.mock('../api/ordersApi', () => ({
  fetchOrder: (...args: unknown[]) => fetchOrder(...args),
  cancelOrder: (...args: unknown[]) => cancelOrder(...args),
}));

vi.mock('../../checkout/api/paymentApi', () => ({
  fetchOrderPayment: (...args: unknown[]) => fetchOrderPayment(...args),
  createOrReusePayment: vi.fn(),
}));

vi.mock('../../checkout/lib/paymentPolling', () => ({
  pollUntilPaidOrTerminal: vi.fn(),
}));

const pendingOrder = {
  id: 'o1',
  orderNumber: 'ORD-1',
  userId: 'u1',
  status: 'PENDING_PAYMENT',
  subtotalCents: 1000,
  discountCents: 0,
  taxCents: 0,
  totalCents: 1000,
  currency: 'USD',
  shippingAddressJson: '{"fullName":"Ada","line1":"1 Main","line2":"","city":"Austin","region":"TX","postalCode":"78701","country":"US"}',
  promotionId: null,
  promotionCode: null,
  items: [
    {
      id: 'li1',
      productId: 'p1',
      productName: 'Mug',
      sku: 'MUG',
      unitPriceCents: 1000,
      quantity: 1,
      lineTotalCents: 1000,
    },
  ],
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe('OrderDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchOrder.mockResolvedValue(pendingOrder);
    fetchOrderPayment.mockRejectedValue(new Error('none'));
  });

  it('renders order totals and cancels unpaid orders', async () => {
    const user = userEvent.setup();
    cancelOrder.mockResolvedValue({ ...pendingOrder, status: 'CANCELLED' });

    render(
      <MemoryRouter initialEntries={['/orders/o1']}>
        <Routes>
          <Route path="/orders/:id" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('ORD-1')).toBeInTheDocument();
    expect(screen.getByText('Mug (MUG) × 1')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Resume payment/i })).toHaveAttribute(
      'href',
      '/orders/o1/pay',
    );

    await user.click(screen.getByRole('button', { name: /Cancel unpaid order/i }));
    await waitFor(() => {
      expect(cancelOrder).toHaveBeenCalledWith('o1');
    });
    expect(await screen.findByText(/CANCELLED/)).toBeInTheDocument();
  });
});
