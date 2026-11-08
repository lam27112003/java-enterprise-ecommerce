import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  __resetApiClientForTests,
  apiRequest,
  setRefreshHandler,
  setUnauthorizedHandler,
} from './apiClient';
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
} from './tokenStorage';
import { ApiError } from './errors';

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
  } as Response;
}

describe('apiClient', () => {
  beforeEach(() => {
    __resetApiClientForTests();
    clearTokens();
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    __resetApiClientForTests();
    clearTokens();
  });

  it('attaches Bearer access token', async () => {
    setAccessToken('access-1');
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { success: true, message: null, data: { ok: true }, timestamp: '' }),
    );

    await apiRequest<{ ok: boolean }>('/platform/info');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer access-1');
  });

  it('refreshes once on 401 then retries (single-flight)', async () => {
    setAccessToken('expired');
    setRefreshToken('refresh-1');

    const fetchMock = vi.mocked(fetch);
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse(401, { success: false, code: 'UNAUTHORIZED', message: 'expired' }),
      )
      .mockResolvedValueOnce(
        jsonResponse(200, {
          success: true,
          message: null,
          data: { ok: true },
          timestamp: '',
        }),
      );

    const refreshHandler = vi.fn().mockImplementation(async () => {
      setAccessToken('access-2');
      setRefreshToken('refresh-2');
      return {
        accessToken: 'access-2',
        refreshToken: 'refresh-2',
        tokenType: 'Bearer',
        expiresInSeconds: 900,
        userId: 'u1',
        email: 'a@b.c',
        fullName: 'A',
        roles: ['CUSTOMER'],
      };
    });
    setRefreshHandler(refreshHandler);

    const data = await apiRequest<{ ok: boolean }>('/auth/me');

    expect(data.ok).toBe(true);
    expect(refreshHandler).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const retryInit = fetchMock.mock.calls[1][1] as RequestInit;
    expect((retryInit.headers as Record<string, string>).Authorization).toBe('Bearer access-2');
    expect(getAccessToken()).toBe('access-2');
  });

  it('coalesces concurrent 401 refreshes into one handler call', async () => {
    setAccessToken('expired');
    setRefreshToken('refresh-1');

    const fetchMock = vi.mocked(fetch);
    fetchMock.mockImplementation(async (_url, init) => {
      const auth = (init?.headers as Record<string, string> | undefined)?.Authorization;
      if (auth === 'Bearer expired') {
        return jsonResponse(401, { success: false, code: 'UNAUTHORIZED', message: 'expired' });
      }
      return jsonResponse(200, {
        success: true,
        message: null,
        data: { ok: true },
        timestamp: '',
      });
    });

    let resolveRefresh!: (value: unknown) => void;
    const refreshPromise = new Promise((resolve) => {
      resolveRefresh = resolve;
    });

    const refreshHandler = vi.fn().mockImplementation(async () => {
      await refreshPromise;
      setAccessToken('access-2');
      return {
        accessToken: 'access-2',
        refreshToken: 'refresh-2',
        tokenType: 'Bearer',
        expiresInSeconds: 900,
        userId: 'u1',
        email: 'a@b.c',
        fullName: 'A',
        roles: ['CUSTOMER'],
      };
    });
    setRefreshHandler(refreshHandler);

    const p1 = apiRequest<{ ok: boolean }>('/auth/me');
    const p2 = apiRequest<{ ok: boolean }>('/orders');

    await Promise.resolve();
    expect(refreshHandler).toHaveBeenCalledTimes(1);
    resolveRefresh(null);

    await expect(p1).resolves.toEqual({ ok: true });
    await expect(p2).resolves.toEqual({ ok: true });
    expect(refreshHandler).toHaveBeenCalledTimes(1);
  });

  it('throws ApiError with backend code', async () => {
    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, {
        success: false,
        code: 'EMAIL_TAKEN',
        message: 'Email already registered',
      }),
    );

    await expect(
      apiRequest('/auth/register', {
        method: 'POST',
        body: {},
        skipAuth: true,
        skipRefresh: true,
      }),
    ).rejects.toMatchObject({
      name: 'ApiError',
      code: 'EMAIL_TAKEN',
      status: 409,
    } satisfies Partial<ApiError>);
  });

  it('clears session and notifies AuthContext on 401 without refresh token (M1)', async () => {
    setAccessToken('expired-access');
    // no refresh token
    const unauthorized = vi.fn();
    setUnauthorizedHandler(unauthorized);
    const refreshHandler = vi.fn();
    setRefreshHandler(refreshHandler);

    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValueOnce(
      jsonResponse(401, { success: false, code: 'UNAUTHORIZED', message: 'expired' }),
    );

    await expect(apiRequest('/auth/me')).rejects.toMatchObject({ status: 401 });

    expect(refreshHandler).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
    expect(unauthorized).toHaveBeenCalledTimes(1);
  });

  it('clears session after refresh failure without retry loop (M1)', async () => {
    setAccessToken('expired-access');
    setRefreshToken('bad-refresh');
    const unauthorized = vi.fn();
    setUnauthorizedHandler(unauthorized);

    const fetchMock = vi.mocked(fetch);
    fetchMock.mockResolvedValue(
      jsonResponse(401, { success: false, code: 'UNAUTHORIZED', message: 'expired' }),
    );

    const refreshHandler = vi.fn().mockResolvedValue(null);
    setRefreshHandler(refreshHandler);

    await expect(apiRequest('/orders')).rejects.toMatchObject({ status: 401 });

    expect(refreshHandler).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
    expect(unauthorized).toHaveBeenCalled();
  });
});
