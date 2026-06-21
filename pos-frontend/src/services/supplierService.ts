import api from './api';

export interface Supplier {
  id: string;
  name: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface SupplierRequest {
  name: string;
  contactPerson?: string;
  email?: string;
  phone?: string;
  address?: string;
  isActive: boolean;
}

export interface PagedSuppliers {
  content: Supplier[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
}

export const supplierService = {
  getSuppliers: async (
    page = 0,
    size = 10,
    options?: { search?: string; sort?: string }
  ) => {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });

    if (options?.search) params.append('search', options.search);
    if (options?.sort) params.append('sort', options.sort);

    const response = await api.get<{ data: PagedSuppliers }>(`/suppliers?${params.toString()}`);
    return response.data.data;
  },

  getSupplier: async (id: string) => {
    const response = await api.get<{ data: Supplier }>(`/suppliers/${id}`);
    return response.data.data;
  },

  createSupplier: async (data: SupplierRequest) => {
    const response = await api.post<{ data: Supplier }>('/suppliers', data);
    return response.data.data;
  },

  updateSupplier: async (id: string, data: SupplierRequest) => {
    const response = await api.put<{ data: Supplier }>(`/suppliers/${id}`, data);
    return response.data.data;
  },

  deleteSupplier: async (id: string) => {
    const response = await api.delete<{ data: null }>(`/suppliers/${id}`);
    return response.data.data;
  },

  toggleStatus: async (id: string) => {
    const response = await api.patch<{ data: Supplier }>(`/suppliers/${id}/status`);
    return response.data.data;
  },

  bulkSetStatus: async (ids: string[], active: boolean) => {
    const response = await api.post<{ data: number }>(`/suppliers/bulk-status`, { ids, active });
    return response.data.data;
  },
};
