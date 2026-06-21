import api from './api';
import {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  MyProfile,
  PinLoginRequest,
  RefreshTokenRequest,
} from '@/types/auth';
import { ApiResponse } from '@/types/common';

/**
 * Service for authentication-related API calls.
 */
export const authService = {
  /**
   * Login with email and password.
   */
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post<ApiResponse<AuthResponse>>('/auth/login', data);
    return response.data.data;
  },

  /**
   * Login with 4-digit PIN (Cashier fast-login).
   */
  pinLogin: async (data: PinLoginRequest): Promise<AuthResponse> => {
    const response = await api.post<ApiResponse<AuthResponse>>('/auth/pin-login', data);
    return response.data.data;
  },

  /**
   * Refresh access token using refresh token.
   */
  refreshToken: async (data: RefreshTokenRequest): Promise<AuthResponse> => {
    const response = await api.post<ApiResponse<AuthResponse>>('/auth/refresh', data);
    return response.data.data;
  },

  /**
   * Log out and revoke refresh tokens.
   */
  logout: async (): Promise<void> => {
    await api.post('/auth/logout');
  },

  /**
   * Get current authenticated user profile.
   */
  getMe: async (): Promise<MyProfile> => {
    const response = await api.get<ApiResponse<MyProfile>>('/auth/me');
    return response.data.data;
  },

  /**
   * Change the signed-in user's password. Used by the forced-first-login flow
   * (scoped token) and by self-service rotations from the profile page.
   */
  changePassword: async (data: ChangePasswordRequest): Promise<void> => {
    await api.post('/auth/change-password', data);
  },

  /**
   * Self-service PIN change from the profile page (requires the current password).
   */
  changePin: async (data: { currentPassword: string; newPin: string }): Promise<void> => {
    await api.post('/users/me/pin', data);
  },

  /**
   * Self-service profile update (own name/phone).
   */
  updateMyProfile: async (data: { firstName: string; lastName: string; phone?: string }): Promise<void> => {
    await api.put('/users/me', data);
  },
};
