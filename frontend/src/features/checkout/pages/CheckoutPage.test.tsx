import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CheckoutPage } from './CheckoutPage';

const navigate = vi.fn();
const fetchCart = vi.fn();
const checkoutOrder = vi.fn();
const previewCartPromo = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => navigate,
  };
});

vi.mock('../../cart/api/cartApi', () => ({
  fetchCart: (...args: unknown[]) => fetchCart(...args),
}));

vi.mock('../../orders/api/ordersApi', () => ({
  checkoutOrder: (...args: unknown[]) => checkoutOrder(...args),
}));

vi.mock('../api/promoApi', () => ({
  previewCartPromo: (...args: unknown[]) => previewCartPromo(...args),
}));

const cart = {
  id: 'c1',
  userId: 'u1',
  currency: 'USD',
  subtotalCents: 2500,
  updatedAt: new Date().toISOString(),
  items: [
    {
      id: 'i1',
      productId: 'p1',
      productName: 'Mug',
      sku: 'MUG',
      quantity: 1,
      unitPriceCents: 2500,
      currency: 'USD',
      lineTotalCents: 2500,
      available: true,
    },
  ],
};

describe('CheckoutPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    fetchCart.mockResolvedValue(cart);
  });

  it('renders cart estimate and places order with Idempotency-Key path', async () => {
    const user = userEvent.setup({ delay: null });
    checkoutOrder.mockResolvedValue({
      id: 'ord-1',
      orderNumber: 'O-1',
      status: 'PENDING_PAYMENT',
      totalCents: 2500,
      currency: 'USD',
      items: [],
    });

    render(
      <MemoryRouter>
        <CheckoutPage />
      </MemoryRouter>,
    );

    await screen.findByText('Mug × 1');
    expect(screen.getByText(/Estimated total/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Full name/i), 'Ada Lovelace');
    await user.type(screen.getByLabelText(/Address line 1/i), '1 Main St');
    await user.type(screen.getByLabelText(/^City/i), 'Austin');
    await user.type(screen.getByLabelText(/State/i), 'TX');
    await user.type(screen.getByLabelText(/Postal code/i), '78701');

    await user.click(screen.getByRole('button', { name: /Place order/i }));

    await waitFor(() => {
      expect(checkoutOrder).toHaveBeenCalled();
    });
    const [, key] = checkoutOrder.mock.calls[0] as [unknown, string];
    expect(key).toMatch(/^[0-9a-f-]{36}$/i);
    expect(navigate).toHaveBeenCalledWith('/orders/ord-1/pay', { replace: true });
  });

  it('shows empty cart state', async () => {
    fetchCart.mockResolvedValue({ ...cart, items: [], subtotalCents: 0 });
    render(
      <MemoryRouter>
        <CheckoutPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText(/Nothing to checkout/i)).toBeInTheDocument();
  });
});
