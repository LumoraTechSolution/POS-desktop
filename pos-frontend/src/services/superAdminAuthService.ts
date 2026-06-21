import superAdminApi from './superAdminApi';
import { ApiResponse } from '@/types/common';
import { SuperAdminUser } from '@/stores/superAdminStore';

export interface SuperAdminLoginRequest {
  email: string;
  password?: string;
}

export interface SuperAdminAuthResponse {
  accessToken: string;
  refreshToken: string | null;
  tokenType: string;
  expiresIn: number;
  passwordChangeRequired: boolean;
  superAdmin: SuperAdminUser;
}

export interface SuperAdminChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface SuperAdminProfileResponse {
  id: string;
  email: string;
  fullName: string;
  firstName?: string;
  lastName?: string;
  createdAt?: string;
  lastLoginAt?: string;
  lastLoginIp?: string;
  lastLoginUserAgent?: string;
  passwordLastChangedAt?: string;
}

export const superAdminAuthService = {
  login: async (data: SuperAdminLoginRequest): Promise<SuperAdminAuthResponse> => {
    const response = await superAdminApi.post<ApiResponse<SuperAdminAuthResponse>>('/auth/login', data);
    return response.data.data;
  },

  refresh: async (refreshToken: string): Promise<SuperAdminAuthResponse> => {
    const response = await superAdminApi.post<ApiResponse<SuperAdminAuthResponse>>('/auth/refresh', { refreshToken });
    return response.data.data;
  },

  logout: async (refreshToken: string | null): Promise<void> => {
    try {
      await superAdminApi.post('/auth/logout', refreshToken ? { refreshToken } : {});
    } catch {
      // Logout must succeed locally even if the network call fails.
    }
  },

  changePassword: async (data: SuperAdminChangePasswordRequest): Promise<void> => {
    await superAdminApi.post('/auth/change-password', data);
  },

  getProfile: async (): Promise<SuperAdminProfileResponse> => {
    const response = await superAdminApi.get<ApiResponse<SuperAdminProfileResponse>>('/auth/me');
    return response.data.data;
  },

  ping: async (): Promise<boolean> => {
    try {
      await superAdminApi.get('/auth/ping');
      return true;
    } catch {
      return false;
    }
  },
};
