import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  refreshTokensWithBody,
  setRefreshHandler,
  setUnauthorizedHandler,
} from '../../shared/lib/apiClient';
import { clearTokens, getAccessToken, getRefreshToken } from '../../shared/lib/tokenStorage';
import type { AuthTokens, AuthUser, LoginRequest, RegisterRequest } from '../../shared/types/auth';
import { isAdmin, isCustomer } from '../../shared/types/auth';
import * as authApi from './authApi';

type AuthStatus = 'bootstrapping' | 'authenticated' | 'anonymous';

type AuthContextValue = {
  status: AuthStatus;
  user: AuthUser | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isCustomer: boolean;
  login: (request: LoginRequest) => Promise<AuthTokens>;
  register: (request: RegisterRequest) => Promise<AuthTokens>;
  logout: () => Promise<void>;
  refreshSession: () => Promise<AuthTokens | null>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function toUser(tokens: AuthTokens): AuthUser {
  return {
    userId: tokens.userId,
    email: tokens.email,
    fullName: tokens.fullName,
    roles: tokens.roles,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('bootstrapping');
  const [user, setUser] = useState<AuthUser | null>(null);

  const applyTokens = useCallback((tokens: AuthTokens) => {
    setUser(toUser(tokens));
    setStatus('authenticated');
  }, []);

  const markAnonymous = useCallback(() => {
    setUser(null);
    setStatus('anonymous');
  }, []);

  const refreshSession = useCallback(async () => {
    const tokens = await refreshTokensWithBody();
    if (tokens) {
      applyTokens(tokens);
      return tokens;
    }
    markAnonymous();
    return null;
  }, [applyTokens, markAnonymous]);

  useEffect(() => {
    setRefreshHandler(refreshSession);
    setUnauthorizedHandler(markAnonymous);
    return () => {
      setRefreshHandler(null);
      setUnauthorizedHandler(null);
    };
  }, [refreshSession, markAnonymous]);

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      if (getAccessToken()) {
        try {
          const profile = await authApi.fetchMe();
          if (cancelled) {
            return;
          }
          setUser({
            userId: profile.id,
            email: profile.email,
            fullName: profile.fullName,
            roles: profile.roles,
          });
          setStatus('authenticated');
          return;
        } catch {
          // fall through to refresh
        }
      }

      if (getRefreshToken()) {
        const tokens = await refreshTokensWithBody();
        if (cancelled) {
          return;
        }
        if (tokens) {
          applyTokens(tokens);
          return;
        }
      }

      if (!cancelled) {
        clearTokens();
        markAnonymous();
      }
    }

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [applyTokens, markAnonymous]);

  const login = useCallback(
    async (request: LoginRequest) => {
      const tokens = await authApi.login(request);
      applyTokens(tokens);
      return tokens;
    },
    [applyTokens],
  );

  const register = useCallback(
    async (request: RegisterRequest) => {
      const tokens = await authApi.register(request);
      applyTokens(tokens);
      return tokens;
    },
    [applyTokens],
  );

  const logout = useCallback(async () => {
    await authApi.logout();
    markAnonymous();
  }, [markAnonymous]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      isAuthenticated: status === 'authenticated' && user != null,
      isAdmin: isAdmin(user?.roles),
      isCustomer: isCustomer(user?.roles),
      login,
      register,
      logout,
      refreshSession,
    }),
    [status, user, login, register, logout, refreshSession],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
