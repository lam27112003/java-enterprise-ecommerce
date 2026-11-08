/**
 * P7-D11: access token in memory; refresh token in sessionStorage.
 * Access is never persisted to disk; refresh survives tab reload within the session.
 */

const REFRESH_KEY = 'ee.auth.refreshToken';

let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getRefreshToken(): string | null {
  try {
    return sessionStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

export function setRefreshToken(token: string | null): void {
  try {
    if (token == null || token === '') {
      sessionStorage.removeItem(REFRESH_KEY);
    } else {
      sessionStorage.setItem(REFRESH_KEY, token);
    }
  } catch {
    // sessionStorage may be unavailable (private mode); ignore
  }
}

export function clearTokens(): void {
  accessToken = null;
  setRefreshToken(null);
}

export function storeTokens(access: string, refresh: string): void {
  setAccessToken(access);
  setRefreshToken(refresh);
}
