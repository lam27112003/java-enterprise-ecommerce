import { useCallback, useEffect, useState } from 'react';
import { isApiError } from '../../../shared/lib/errors';
import { EmptyState } from '../../../shared/ui/EmptyState';
import { ErrorState } from '../../../shared/ui/ErrorState';
import { LoadingState } from '../../../shared/ui/LoadingState';
import { PaginationControls } from '../../catalog/components/PaginationControls';
import { fetchAdminUsers, updateAdminUserStatus } from '../api/usersApi';
import type { AdminUser, UserStatus } from '../types/adminTypes';

const PAGE_SIZE = 20;

export function AdminUsersPage() {
  const [q, setQ] = useState('');
  const [draftQ, setDraftQ] = useState('');
  const [page, setPage] = useState(0);
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchAdminUsers(q || undefined, page, PAGE_SIZE);
      setUsers(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      setError(isApiError(err) ? err.message : 'Failed to load users');
    } finally {
      setLoading(false);
    }
  }, [q, page]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onStatusChange(userId: string, status: UserStatus) {
    setBusyId(userId);
    setActionError(null);
    try {
      const updated = await updateAdminUserStatus(userId, status);
      setUsers((prev) => prev.map((u) => (u.id === userId ? updated : u)));
    } catch (err) {
      setActionError(isApiError(err) ? err.message : 'Could not update user status');
    } finally {
      setBusyId(null);
    }
  }

  function onSearch(e: React.FormEvent) {
    e.preventDefault();
    setPage(0);
    setQ(draftQ.trim());
  }

  if (loading && users.length === 0) {
    return <LoadingState label="Loading users…" />;
  }

  if (error && users.length === 0) {
    return <ErrorState message={error} onRetry={() => void load()} />;
  }

  return (
    <section className="space-y-6">
      <header>
        <h1 className="font-display text-3xl font-bold text-brand-900">Users</h1>
        <p className="mt-1 font-sans text-sm text-brand-700">Search accounts and change status (no role editing).</p>
      </header>

      <form onSubmit={onSearch} className="flex flex-wrap gap-2">
        <input
          value={draftQ}
          onChange={(e) => setDraftQ(e.target.value)}
          placeholder="Search email or name"
          className="min-w-[200px] flex-1 rounded-lg border border-brand-200 bg-white px-3 py-2 font-sans text-sm"
        />
        <button
          type="submit"
          className="rounded-lg bg-brand-700 px-4 py-2 font-sans text-sm font-semibold text-white"
        >
          Search
        </button>
      </form>

      {actionError && (
        <p className="font-sans text-sm text-red-700" role="alert">
          {actionError}
        </p>
      )}

      {users.length === 0 ? (
        <EmptyState title="No users" message="Try a different search." />
      ) : (
        <>
          <div className="overflow-x-auto rounded-2xl border border-brand-100 bg-white/80">
            <table className="min-w-full font-sans text-sm">
              <thead className="border-b border-brand-100 text-left text-brand-600">
                <tr>
                  <th className="px-4 py-2">Email</th>
                  <th className="px-4 py-2">Name</th>
                  <th className="px-4 py-2">Status</th>
                  <th className="px-4 py-2">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id} className="border-b border-brand-50">
                    <td className="px-4 py-2">{user.email}</td>
                    <td className="px-4 py-2">{user.fullName}</td>
                    <td className="px-4 py-2">{user.status}</td>
                    <td className="px-4 py-2">
                      <label className="sr-only" htmlFor={`status-${user.id}`}>
                        Change status for {user.email}
                      </label>
                      <select
                        id={`status-${user.id}`}
                        value={user.status}
                        disabled={busyId === user.id}
                        onChange={(e) => void onStatusChange(user.id, e.target.value as UserStatus)}
                        className="rounded-lg border border-brand-200 bg-white px-2 py-1"
                      >
                        <option value="ACTIVE">ACTIVE</option>
                        <option value="DISABLED">DISABLED</option>
                        <option value="SUSPENDED">SUSPENDED</option>
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <PaginationControls
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={setPage}
          />
        </>
      )}
    </section>
  );
}
