export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  email: string;
  fullName: string;
  roles: string[];
};

export type UserProfile = {
  id: string;
  email: string;
  fullName: string;
  status: string;
  roles: string[];
  createdAt: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type RegisterRequest = {
  email: string;
  password: string;
  fullName: string;
};

export type AuthUser = {
  userId: string;
  email: string;
  fullName: string;
  roles: string[];
};

export function hasRole(roles: string[] | undefined, role: string): boolean {
  if (!roles) {
    return false;
  }
  const normalized = role.toUpperCase();
  return roles.some((r) => r.toUpperCase() === normalized || r.toUpperCase() === `ROLE_${normalized}`);
}

export function isAdmin(roles: string[] | undefined): boolean {
  return hasRole(roles, 'ADMIN');
}

export function isCustomer(roles: string[] | undefined): boolean {
  return hasRole(roles, 'CUSTOMER');
}
