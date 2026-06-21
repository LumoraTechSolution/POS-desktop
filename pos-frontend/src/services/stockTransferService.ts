import api from "./api";
import { ApiResponse, Page } from "@/types/common";

export interface StockTransfer {
  id: string;
  sourceBranchId: string;
  sourceBranchName: string;
  destinationBranchId: string;
  destinationBranchName: string;
  productId: string;
  productName: string;
  productSku: string;
  quantity: number;
  status: 'PENDING' | 'IN_TRANSIT' | 'COMPLETED' | 'CANCELLED';
  notes?: string;
  completedAt?: string;
  createdAt: string;
}

export interface StockTransferRequest {
  sourceBranchId: string;
  destinationBranchId: string;
  productId: string;
  quantity: number;
  notes?: string;
}

export const stockTransferService = {
  getTransfers: (page = 0, size = 10, status?: string) => {
    const params = new URLSearchParams();
    params.set('page', page.toString());
    params.set('size', size.toString());
    if (status) params.set('status', status);
    
    return api.get<ApiResponse<Page<StockTransfer>>>(`/stock-transfers?${params.toString()}`)
      .then(res => res.data.data);
  },

  getTransfer: (id: string) =>
    api.get<ApiResponse<StockTransfer>>(`/stock-transfers/${id}`)
      .then(res => res.data.data),

  createTransfer: (data: StockTransferRequest) =>
    api.post<ApiResponse<StockTransfer>>("/stock-transfers", data)
      .then(res => res.data.data),

  markInTransit: (id: string) =>
    api.put<ApiResponse<StockTransfer>>(`/stock-transfers/${id}/in-transit`)
      .then(res => res.data.data),

  completeTransfer: (id: string) =>
    api.put<ApiResponse<StockTransfer>>(`/stock-transfers/${id}/complete`)
      .then(res => res.data.data),

  cancelTransfer: (id: string) =>
    api.put<ApiResponse<StockTransfer>>(`/stock-transfers/${id}/cancel`)
      .then(res => res.data.data),
};
