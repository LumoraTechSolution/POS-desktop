import api from './api';

export type AdjustmentType = 
  | 'STOCK_IN' 
  | 'STOCK_OUT' 
  | 'SALE' 
  | 'RETURN' 
  | 'RECONCILIATION' 
  | 'DAMAGE' 
  | 'TRANSFER_IN' 
  | 'TRANSFER_OUT';

export interface InventoryAdjustmentRequest {
  productId: string;
  branchId: string;
  type: AdjustmentType;
  quantity: number;
  reason?: string;
  referenceId?: string;
}

export interface StockTransferRequest {
  productId: string;
  sourceBranchId: string;
  destinationBranchId: string;
  quantity: number;
  reason?: string;
}

export interface InventoryAdjustmentResponse {
  id: string;
  productName: string;
  branchName: string;
  type: AdjustmentType;
  quantity: number;
  previousQuantity: number;
  newQuantity: number;
  reason: string;
  referenceId: string;
  createdAt: string;
}

export const inventoryAdjustmentService = {
  adjustStock: async (request: InventoryAdjustmentRequest) => {
    const response = await api.post('/inventory/adjust', request);
    return response.data;
  },

  transferStock: async (request: StockTransferRequest) => {
    const response = await api.post('/inventory/transfer', request);
    return response.data;
  },

  getAdjustments: async (productId: string): Promise<InventoryAdjustmentResponse[]> => {
    const response = await api.get(`/inventory/adjustments/${productId}`);
    return response.data.data;
  }
};
