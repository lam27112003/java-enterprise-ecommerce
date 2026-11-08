import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PayOrderPage } from './PayOrderPage';

const fetchOrder = vi.fn();
const createOrReusePayment = vi.fn();
const fetchOrderPayment = vi.fn();

vi.mock('../../orders/api/ordersApi', () => ({
  fetchOrder: (...args: unknown[]) => fetchOrder(...args),
}));

vi.mock('../api/paymentApi', () => ({
  createOrReusePayment: (...args: unknown[]) => createOrReusePayment(...args),
  fetchOrderPayment: (...args: unknown[]) => fetchOrderPayment(...args),
}));

vi.mock('@stripe/stripe-js', () => ({
  loadStripe: vi.fn(async () => null),
}));

vi.mock('@stripe/react-stripe-js', () => ({
  Elements: ({ children }: { children: unknown }) => <div data-testid="elements">{children as never}</div>,
  PaymentElement: () => <div data-testid="payment-element" />,
  useStripe: () => null,
  useElements: () => null,
}));

vi.mock('../../../shared/lib/env', async () => {
  const actual = await vi.importActual<typeof import('../../../shared/lib/env')>(
    '../../../shared/lib/env',
  );
  return {
    ...actual,
    stripePublishableKey: () => 'pk_test_unit',
    assertStripeTestPublishableKey: () => undefined,
  };
});

describe('PayOrderPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads pay intent but does not claim PAID without backend status', async () => {
    fetchOrder.mockResolvedValue({
      id: 'o1',
      orderNumber: 'ORD-1',
      status: 'PENDING_PAYMENT',
      totalCents: 1500,
      currency: 'USD',
    });
    createOrReusePayment.mockResolvedValue({
      id: 'pay1',
      orderId: 'o1',
      status: 'REQUIRES_ACTION',
      amountCents: 1500,
      currency: 'USD',
      providerRef: 'pi_test',
      clientSecret: 'secret_test',
    });

    render(
      <MemoryRouter initialEntries={['/orders/o1/pay']}>
        <Routes>
          <Route path="/orders/:id/pay" element={<PayOrderPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(createOrReusePayment).toHaveBeenCalledWith('o1');
    });
    expect(screen.queryByText(/Payment confirmed/i)).not.toBeInTheDocument();
    expect(screen.getByText(/PENDING_PAYMENT/)).toBeInTheDocument();
    expect(screen.getByText(/webhook/i)).toBeInTheDocument();
  });

  it('shows paid panel when order already PAID', async () => {
    fetchOrder.mockResolvedValue({
      id: 'o1',
      orderNumber: 'ORD-1',
      status: 'PAID',
      totalCents: 1500,
      currency: 'USD',
    });
    fetchOrderPayment.mockResolvedValue({
      id: 'pay1',
      orderId: 'o1',
      userId: 'u1',
      status: 'SUCCEEDED',
      amountCents: 1500,
      currency: 'USD',
      providerRef: 'pi_test',
      createdAt: '',
      updatedAt: '',
    });

    render(
      <MemoryRouter initialEntries={['/orders/o1/pay']}>
        <Routes>
          <Route path="/orders/:id/pay" element={<PayOrderPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText(/Payment confirmed/i)).toBeInTheDocument();
    expect(createOrReusePayment).not.toHaveBeenCalled();
  });
});
