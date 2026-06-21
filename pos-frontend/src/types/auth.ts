
export interface User {
  id: string;
  tenantId: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  permissions: string[];
  featuresEnabled: string[];
  planTier: string;
  maxLocations: number;
  maxUsers: number;
  maxProducts: number;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
  /** When true, accessToken is a change-password-scoped token; the client must
   *  force the user through a password change before entering the app. */
  passwordChangeRequired?: boolean;
}

export interface MyProfileBranchRef {
  id: string;
  name: string;
}

/** Returned by GET /auth/me — the signed-in user's own profile. */
export interface MyProfile {
  id: string;
  tenantId: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string | null;
  roles: string[];
  hasPin: boolean;
  primaryBranchId: string | null;
  primaryBranchName: string | null;
  branches: MyProfileBranchRef[];
  lastLoginAt: string | null;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface LoginRequest {
  email: string;
  password?: string;
}

export interface PinLoginRequest {
  pin: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
  tenantId: string;
}
