import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminRefundDetailPage } from './AdminRefundDetailPage';

const fetchAdminRefund = vi.fn();
const fetchRefundAudits = vi.fn();
const confirmExternalRefund = vi.fn();
const reopenAndSubmitRefund = vi.fn();

vi.mock('../api/refundsApi', () => ({
  fetchAdminRefund: (...args: unknown[]) => fetchAdminRefund(...args),
  fetchRefundAudits: (...args: unknown[]) => fetchRefundAudits(...args),
  confirmExternalRefund: (...args: unknown[]) => confirmExternalRefund(...args),
  reopenAndSubmitRefund: (...args: unknown[]) => reopenAndSubmitRefund(...args),
}));

const detail = {
  refund: {
    id: 'r-1',
    paymentId: 'p-1',
    orderId: 'o-1',
    status: 'RECONCILIATION_REQUIRED' as const,
    reasonCode: 'CUSTOMER_REQUEST',
    amountCents: 2500,
    currency: 'USD',
    providerRefundRef: null,
    createdAt: new Date().toISOString(),
    refundedAt: null,
  },
  idempotencyKey: 'key',
  paymentOrderId: 'o-1',
  paymentAmountCents: 5000,
  paymentCurrency: 'USD',
  paymentProviderRef: 'pi_test',
  audits: [],
};

describe('AdminRefundDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchAdminRefund.mockResolvedValue(detail);
    fetchRefundAudits.mockResolvedValue([]);
  });

  it('calls confirm API and shows success only after response', async () => {
    const user = userEvent.setup();
    let resolveConfirm!: (value: { status: string }) => void;
    confirmExternalRefund.mockImplementation(
      () =>
        new Promise<{ status: string }>((resolve) => {
          resolveConfirm = resolve;
        }),
    );

    render(
      <MemoryRouter initialEntries={['/admin/refunds/r-1']}>
        <Routes>
          <Route path="/admin/refunds/:id" element={<AdminRefundDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: /Refund r-1/i })).toBeInTheDocument();
    expect(screen.queryByText(/External refund confirmed/i)).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Provider refund reference/i), 're_123');
    await user.click(screen.getByRole('button', { name: /Confirm external/i }));

    expect(confirmExternalRefund).toHaveBeenCalledWith('r-1', {
      providerRefundRef: 're_123',
      note: undefined,
    });
    expect(screen.queryByText(/External refund confirmed/i)).not.toBeInTheDocument();

    resolveConfirm({ status: 'CONFIRMED' });

    expect(await screen.findByText(/External refund confirmed \(CONFIRMED\)/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchAdminRefund).toHaveBeenCalledTimes(2);
    });
  });
});
