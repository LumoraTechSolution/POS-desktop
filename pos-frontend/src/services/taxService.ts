import api from "./api";
import { ApiResponse } from "@/types/common";

export interface TaxRate {
  id: string;
  name: string;
  rate: number;         // Decimal (e.g. 0.10)
  ratePercent: number;  // Human-friendly (e.g. 10.00)
  description?: string;
  isDefault: boolean;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TaxRateRequest {
  name: string;
  rate: number;  // As percentage (e.g. 10 for 10%). Backend converts to decimal.
  description?: string;
  isDefault: boolean;
  isActive: boolean;
}

export const taxService = {
  getAllTaxRates: () =>
    api.get<ApiResponse<TaxRate[]>>("/tax-rates").then(res => res.data.data),

  getActiveTaxRates: () =>
    api.get<ApiResponse<TaxRate[]>>("/tax-rates/active").then(res => res.data.data),

  getTaxRate: (id: string) =>
    api.get<ApiResponse<TaxRate>>(`/tax-rates/${id}`).then(res => res.data.data),

  createTaxRate: (data: TaxRateRequest) =>
    api.post<ApiResponse<TaxRate>>("/tax-rates", data).then(res => res.data.data),

  updateTaxRate: (id: string, data: TaxRateRequest) =>
    api.put<ApiResponse<TaxRate>>(`/tax-rates/${id}`, data).then(res => res.data.data),

  deleteTaxRate: (id: string) =>
    api.delete<ApiResponse<void>>(`/tax-rates/${id}`).then(res => res.data),
};
