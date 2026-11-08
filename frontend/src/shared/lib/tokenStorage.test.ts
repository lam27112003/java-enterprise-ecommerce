import { afterEach, describe, expect, it } from 'vitest';
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
  storeTokens,
} from './tokenStorage';

describe('tokenStorage', () => {
  afterEach(() => {
    clearTokens();
  });

  it('keeps access token in memory only', () => {
    setAccessToken('a1');
    expect(getAccessToken()).toBe('a1');
    expect(sessionStorage.getItem('ee.auth.refreshToken')).toBeNull();
  });

  it('persists refresh token in sessionStorage', () => {
    setRefreshToken('r1');
    expect(getRefreshToken()).toBe('r1');
    expect(sessionStorage.getItem('ee.auth.refreshToken')).toBe('r1');
  });

  it('storeTokens sets both', () => {
    storeTokens('a2', 'r2');
    expect(getAccessToken()).toBe('a2');
    expect(getRefreshToken()).toBe('r2');
  });
});
