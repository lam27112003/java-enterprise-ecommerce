import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DashboardPage } from './DashboardPage';

const fetchDashboardSummary = vi.fn();

vi.mock('../api/dashboardApi', () => ({
  fetchDashboardSummary: (...args: unknown[]) => fetchDashboardSummary(...args),
}));

const summary = {
  totalProducts: 42,
  activeProducts: 30,
  lowStockProducts: 3,
  openOrders: 7,
  ordersAwaitingFulfillment: 2,
  refundsRequiringReconciliation: 1,
  revenuePeriodFrom: '2026-01-01T00:00:00.000Z',
  revenuePeriodTo: '2026-01-31T23:59:59.999Z',
  revenuePeriodDescription: 'January 2026',
  revenueGrossCentsByCurrency: { USD: 50000 },
  revenueRefundedCentsByCurrency: { USD: 1000 },
  revenueNetCentsByCurrency: { USD: 49000 },
  ordersCreatedInWindow: 15,
};

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchDashboardSummary.mockResolvedValue(summary);
  });

  it('renders dashboard KPIs and payment-period revenue labels', async () => {
    render(<DashboardPage />);

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByText(/Payment-period revenue \(January 2026\)/)).toBeInTheDocument();
    expect(screen.getByText('Total products')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByText('Gross revenue (payment period)')).toBeInTheDocument();
    expect(screen.getByText(/\$500\.00/)).toBeInTheDocument();
    expect(screen.getByText('Net revenue (payment period)')).toBeInTheDocument();
  });
});
