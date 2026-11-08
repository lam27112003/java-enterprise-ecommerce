import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminUsersPage } from './AdminUsersPage';

const fetchAdminUsers = vi.fn();
const updateAdminUserStatus = vi.fn();

vi.mock('../api/usersApi', () => ({
  fetchAdminUsers: (...args: unknown[]) => fetchAdminUsers(...args),
  updateAdminUserStatus: (...args: unknown[]) => updateAdminUserStatus(...args),
}));

const userRow = {
  id: 'u-1',
  email: 'cust@example.com',
  fullName: 'Customer One',
  status: 'ACTIVE' as const,
  roles: ['CUSTOMER'],
  createdAt: new Date().toISOString(),
};

describe('AdminUsersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchAdminUsers.mockResolvedValue({
      content: [userRow],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    updateAdminUserStatus.mockResolvedValue({ ...userRow, status: 'DISABLED' });
  });

  it('calls updateAdminUserStatus when status changes', async () => {
    const ui = userEvent.setup();
    render(<AdminUsersPage />);

    expect(await screen.findByText('cust@example.com')).toBeInTheDocument();
    const select = screen.getByLabelText(/Change status for cust@example.com/i);
    await ui.selectOptions(select, 'DISABLED');

    await waitFor(() => {
      expect(updateAdminUserStatus).toHaveBeenCalledWith('u-1', 'DISABLED');
    });
  });
});
