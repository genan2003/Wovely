export type Role = 'ROLE_USER' | 'ROLE_SELLER' | 'ROLE_ADMIN';

/** The signed-in user as returned by POST /api/auth/signin and /refresh, minus the refresh token. */
export interface AuthUser {
  id: string;
  username: string;
  email: string;
  roles: Role[];
  accessToken: string;
  tokenType?: string;
  expiresInMs?: number;
}

export interface AuthResponse extends AuthUser {
  refreshToken?: string;
}

export interface MessageResponse {
  message: string;
}
