import { apiRequest } from '../../shared/lib/apiClient';
import { clearTokens, getRefreshToken, storeTokens } from '../../shared/lib/tokenStorage';
import type { AuthTokens, LoginRequest, RegisterRequest, UserProfile } from '../../shared/types/auth';

export async function login(request: LoginRequest): Promise<AuthTokens> {
  const tokens = await apiRequest<AuthTokens>('/auth/login', {
    method: 'POST',
    body: request,
    skipAuth: true,
    skipRefresh: true,
  });
  storeTokens(tokens.accessToken, tokens.refreshToken);
  return tokens;
}

export async function register(request: RegisterRequest): Promise<AuthTokens> {
  const tokens = await apiRequest<AuthTokens>('/auth/register', {
    method: 'POST',
    body: request,
    skipAuth: true,
    skipRefresh: true,
  });
  storeTokens(tokens.accessToken, tokens.refreshToken);
  return tokens;
}

export async function fetchMe(): Promise<UserProfile> {
  return apiRequest<UserProfile>('/auth/me');
}

export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();
  try {
    await apiRequest<null>('/auth/logout', {
      method: 'POST',
      body: refreshToken ? { refreshToken } : {},
      skipRefresh: true,
    });
  } catch {
    // Best-effort logout; always clear local tokens
  } finally {
    clearTokens();
  }
}
