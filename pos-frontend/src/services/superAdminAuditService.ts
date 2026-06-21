import superAdminApi from './superAdminApi';
import { ApiResponse } from '@/types/common';

export interface SuperAdminAuditResponse {
  id: string;
  tenantId: string;
  tenantName: string;
  userId: string;
  action: string;
  entityType: string;
  entityId: string;
  oldValue: string;
  newValue: string;
  ipAddress: string;
  createdAt: string;
}

export interface PagedAuditResponse {
  content: SuperAdminAuditResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
}

export const superAdminAuditService = {
  getGlobalAuditLogs: async (
    page: number = 0, 
    size: number = 50, 
    search?: string,
    startDate?: string,
    endDate?: string
  ): Promise<PagedAuditResponse> => {
    let url = `/audit?page=${page}&size=${size}`;
    if (search) {
      url += `&search=${encodeURIComponent(search)}`;
    }
    if (startDate) {
      url += `&startDate=${encodeURIComponent(startDate)}`;
    }
    if (endDate) {
      url += `&endDate=${encodeURIComponent(endDate)}`;
    }
    const response = await superAdminApi.get<ApiResponse<PagedAuditResponse>>(url);
    return response.data.data;
  },
};
