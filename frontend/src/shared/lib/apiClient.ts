import { apiBase } from './env';
import { ApiError } from './errors';
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  storeTokens,
} from './tokenStorage';
import type { ApiErrorBody, ApiSuccess } from '../types/api';
import type { AuthTokens } from '../types/auth';

export type RequestOptions = {
  method?: string;
  body?: unknown;
  /** Skip Authorization header (login/register/refresh). */
  skipAuth?: boolean;
  /** Skip 401→refresh retry (used by refresh itself). */
  skipRefresh?: boolean;
  headers?: Record<string, string>;
  signal?: AbortSignal;
};

type RefreshHandler = () => Promise<AuthTokens | null>;
type UnauthorizedHandler = () => void;

let refreshHandler: RefreshHandler | null = null;
let unauthorizedHandler: UnauthorizedHandler | null = null;
let refreshInFlight: Promise<AuthTokens | null> | null = null;

/** Auth layer registers the refresh implementation to avoid circular imports. */
export function setRefreshHandler(handler: RefreshHandler | null): void {
  refreshHandler = handler;
}

/**
 * Called when the client clears the session after a terminal 401 (no refresh / refresh failed).
 * AuthContext registers this so React auth state stays in sync with token storage (M1).
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

function clearSession(): void {
  clearTokens();
  unauthorizedHandler?.();
}

async function parseJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

function toApiError(status: number, payload: unknown, fallbackPath?: string): ApiError {
  if (payload && typeof payload === 'object') {
    const body = payload as Partial<ApiErrorBody>;
    if (typeof body.code === 'string' || typeof body.message === 'string') {
      return new ApiError(
        status,
        body.code ?? 'HTTP_ERROR',
        body.message ?? `Request failed with status ${status}`,
        body.fieldErrors ?? [],
        body.path ?? fallbackPath,
      );
    }
  }
  return new ApiError(status, 'HTTP_ERROR', `Request failed with status ${status}`, [], fallbackPath);
}

async function singleFlightRefresh(): Promise<AuthTokens | null> {
  if (!refreshHandler) {
    return null;
  }
  if (!refreshInFlight) {
    refreshInFlight = refreshHandler()
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

/**
 * JSON API client with Bearer auth and single-flight body-based refresh (P7-D11).
 * Refresh uses POST /auth/refresh with `{ refreshToken }` — not cookies.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const base = apiBase();
  const url = path.startsWith('http') ? path : `${base}${path.startsWith('/') ? path : `/${path}`}`;

  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options.headers,
  };

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  if (!options.skipAuth) {
    const token = getAccessToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const execute = () =>
    fetch(url, {
      method: options.method ?? (options.body !== undefined ? 'POST' : 'GET'),
      headers,
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
      signal: options.signal,
    });

  let response = await execute();

  if (response.status === 401 && !options.skipAuth && !options.skipRefresh && getRefreshToken()) {
    const refreshed = await singleFlightRefresh();
    if (refreshed?.accessToken) {
      headers.Authorization = `Bearer ${refreshed.accessToken}`;
      response = await execute();
    }
  }

  const payload = await parseJson(response);

  if (!response.ok) {
    if (response.status === 401 && !options.skipRefresh) {
      // Terminal unauthorized: no refresh token, refresh failed, or retry still 401.
      clearSession();
    }
    throw toApiError(response.status, payload, path);
  }

  if (payload && typeof payload === 'object' && 'success' in payload) {
    const envelope = payload as ApiSuccess<T> | ApiErrorBody;
    if (envelope.success === false) {
      throw toApiError(response.status, envelope, path);
    }
    return (envelope as ApiSuccess<T>).data;
  }

  return payload as T;
}

export async function refreshTokensWithBody(): Promise<AuthTokens | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return null;
  }
  try {
    const tokens = await apiRequest<AuthTokens>('/auth/refresh', {
      method: 'POST',
      body: { refreshToken },
      skipAuth: true,
      skipRefresh: true,
    });
    storeTokens(tokens.accessToken, tokens.refreshToken);
    return tokens;
  } catch {
    clearSession();
    return null;
  }
}

/** Exposed for unit tests to reset single-flight state. */
export function __resetApiClientForTests(): void {
  refreshInFlight = null;
  refreshHandler = null;
  unauthorizedHandler = null;
}
