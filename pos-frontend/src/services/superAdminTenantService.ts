import superAdminApi from './superAdminApi';
import { ApiResponse } from '@/types/common';
import {
  PagedTenantResponse,
  TenantSummaryResponse,
  TenantDetailResponse,
  TenantConfigurationRequest,
  CreateTenantRequest,
  TenantUserResponse,
} from '@/types/superAdmin';

export const superAdminTenantService = {
  /**
   * Fetch paginated list of tenants
   */
  listTenants: async (
    page: number = 0,
    size: number = 20,
    search?: string,
    isActive?: boolean
  ): Promise<PagedTenantResponse> => {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });

    if (search) params.append('search', search);
    if (isActive !== undefined) params.append('isActive', isActive.toString());

    const response = await superAdminApi.get<ApiResponse<PagedTenantResponse>>(`/tenants?${params.toString()}`);
    return response.data.data;
  },

  /**
   * Suspend or Activate a tenant
   */
  toggleTenantStatus: async (tenantId: string, action: 'suspend' | 'activate'): Promise<void> => {
    await superAdminApi.patch(`/tenants/${tenantId}/${action}`);
  },

  /**
   * Provision a new Tenant
   */
  createTenant: async (data: CreateTenantRequest): Promise<TenantSummaryResponse> => {
    const response = await superAdminApi.post<ApiResponse<TenantSummaryResponse>>('/tenants', data);
    return response.data.data;
  },

  /**
   * Fetch full Tenant Detail: identity, configuration, and live usage stats.
   * Used by the Tenant Detail page ([id]/page.tsx).
   */
  getTenantDetail: async (tenantId: string): Promise<TenantDetailResponse> => {
    const response = await superAdminApi.get<ApiResponse<TenantDetailResponse>>(`/tenants/${tenantId}`);
    return response.data.data;
  },

  /**
   * Update a tenant's plan tier, feature flags, and resource limits.
   * Persists the configuration form from the Configuration tab.
   */
  updateTenantConfiguration: async (
    tenantId: string,
    payload: TenantConfigurationRequest
  ): Promise<TenantDetailResponse> => {
    const response = await superAdminApi.put<ApiResponse<TenantDetailResponse>>(
      `/tenants/${tenantId}/config`,
      payload
    );
    return response.data.data;
  },

  /**
   * List a tenant's users (Users tab). Also the "forgot email" lookup surface.
   */
  getTenantUsers: async (tenantId: string): Promise<TenantUserResponse[]> => {
    const response = await superAdminApi.get<ApiResponse<TenantUserResponse[]>>(
      `/tenants/${tenantId}/users`
    );
    return response.data.data;
  },

  /**
   * Reset a tenant user's password. The user must change it on next login.
   */
  resetTenantUserPassword: async (
    tenantId: string,
    userId: string,
    newPassword: string
  ): Promise<void> => {
    await superAdminApi.post(`/tenants/${tenantId}/users/${userId}/reset-password`, { newPassword });
  },
};
