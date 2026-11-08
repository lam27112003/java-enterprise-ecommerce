import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Order } from '../../../shared/types/order';
import { AdminOrderFulfillmentForm } from './AdminOrderFulfillmentForm';

const baseOrder: Order = {
  id: 'o1',
  orderNumber: 'ORD-1',
  userId: 'u1',
  status: 'PROCESSING',
  subtotalCents: 1000,
  discountCents: 0,
  taxCents: 0,
  totalCents: 1000,
  currency: 'USD',
  shippingAddressJson: null,
  promotionId: null,
  promotionCode: null,
  items: [],
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

describe('AdminOrderFulfillmentForm', () => {
  it('requires tracking and carrier before submitting SHIPPED', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);

    render(<AdminOrderFulfillmentForm order={baseOrder} onSubmit={onSubmit} />);

    await user.click(screen.getByRole('button', { name: /Update status/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/Tracking number and carrier are required/i);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits SHIPPED when tracking fields are filled', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);

    render(<AdminOrderFulfillmentForm order={baseOrder} onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText(/Tracking number/i), '1Z999');
    await user.type(screen.getByLabelText(/Carrier/i), 'UPS');
    await user.click(screen.getByRole('button', { name: /Update status/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'SHIPPED',
          trackingNumber: '1Z999',
          carrier: 'UPS',
        }),
      );
    });
  });
});
