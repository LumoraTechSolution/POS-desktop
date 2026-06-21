import api from "./api";
import { ApiResponse } from "@/types/common";

export interface Branch {
  id: string;
  name: string;
  address: string;
  phoneNumber: string;
  isActive: boolean;
  isDefault: boolean;
  createdAt: string;
}

export interface BranchRequest {
  name: string;
  address?: string;
  phoneNumber?: string;
  isActive: boolean;
}

export const branchService = {
  getAllBranches: () =>
    api.get<ApiResponse<Branch[]>>("/branches").then(res => res.data.data),

  /** Branches the current user may operate at (filtered when branch restrictions are on). */
  getMyBranches: () =>
    api.get<ApiResponse<Branch[]>>("/branches/me").then(res => res.data.data),

  getBranch: (id: string) => 
    api.get<ApiResponse<Branch>>(`/branches/${id}`).then(res => res.data.data),
  
  createBranch: (data: BranchRequest) => 
    api.post<ApiResponse<Branch>>("/branches", data).then(res => res.data.data),
  
  updateBranch: (id: string, data: BranchRequest) => 
    api.put<ApiResponse<Branch>>(`/branches/${id}`, data).then(res => res.data.data),
};
