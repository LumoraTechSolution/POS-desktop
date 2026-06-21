import api from './api';

export type POStatus = 'DRAFT' | 'ORDERED' | 'PARTIAL' | 'RECEIVED' | 'CANCELLED';

export interface PurchaseOrderItem {
  id: string;
  productId: string;
  productName: string;
  sku: string;
  orderedQuantity: number;
  receivedQuantity: number;
  unitCost: number;
  totalCost: number;
}

export interface PurchaseOrder {
  id: string;
  poNumber: string;
  supplierId: string;
  supplierName: string;
  branchId: string;
  branchName: string;
  status: POStatus;
  expectedDate?: string;
  totalAmount: number;
  notes?: string;
  createdBy: string;
  createdByName?: string;
  receivedBy?: string;
  receivedByName?: string;
  items: PurchaseOrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface PurchaseOrderItemRequest {
  productId: string;
  quantity: number;
  unitCost: number;
}

export interface PurchaseOrderRequest {
  supplierId: string;
  branchId: string;
  expectedDate?: string;
  notes?: string;
  items: PurchaseOrderItemRequest[];
}

export interface ReceivePoItemRequest {
  poItemId: string;
  receivedQuantity: number;
}

export interface PagedPurchaseOrders {
  content: PurchaseOrder[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
}

export const purchaseOrderService = {
  getPurchaseOrders: async (
    page = 0,
    size = 10,
    options?: {
      sort?: string;
      status?: POStatus;
      supplierId?: string;
      search?: string;
    }
  ) => {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
    });

    if (options?.sort) params.append('sort', options.sort);
    if (options?.status) params.append('status', options.status);
    if (options?.supplierId) params.append('supplierId', options.supplierId);
    if (options?.search && options.search.trim()) params.append('search', options.search.trim());

    const response = await api.get<{ data: PagedPurchaseOrders }>(`/purchase-orders?${params.toString()}`);
    return response.data.data;
  },

  getPurchaseOrder: async (id: string) => {
    const response = await api.get<{ data: PurchaseOrder }>(`/purchase-orders/${id}`);
    return response.data.data;
  },

  createPurchaseOrder: async (data: PurchaseOrderRequest) => {
    const response = await api.post<{ data: PurchaseOrder }>('/purchase-orders', data);
    return response.data.data;
  },

  updatePurchaseOrderStatus: async (id: string, status: POStatus) => {
    const response = await api.patch<{ data: PurchaseOrder }>(`/purchase-orders/${id}/status?status=${status}`);
    return response.data.data;
  },

  receivePurchaseOrder: async (id: string, items: ReceivePoItemRequest[]) => {
    const response = await api.post<{ data: PurchaseOrder }>(`/purchase-orders/${id}/receive`, items);
    return response.data.data;
  },
};
