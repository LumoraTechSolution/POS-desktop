import api from "./api";
import { ApiResponse } from "@/types/common";

export interface BranchSummary {
  id: string;
  name: string;
}

export interface UserResponse {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone: string;
  active: boolean;
  /** Whether the user has a 4-digit PIN set (required for manager POS overrides). */
  hasPin: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  roles: string[];
  /** Branch-access assignment (relevant when BRANCH_RESTRICTIONS is enabled). */
  primaryBranchId: string | null;
  branches: BranchSummary[];
}

export interface CreateUserRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  pin?: string;
  phone?: string;
  roleNames: string[];
  branchIds?: string[];
  primaryBranchId?: string;
}

export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  roleNames?: string[];
  /** Set/replace the 4-digit PIN. Omit (or leave blank) to keep the existing PIN. */
  pin?: string;
  branchIds?: string[];
  primaryBranchId?: string;
}

export interface UpdateBranchesRequest {
  branchIds: string[];
  primaryBranchId?: string;
}

/** A user caught up in a shared-PIN collision. The PIN value is never returned. */
export interface PinConflictUser {
  id: string;
  firstName: string;
  lastName: string;
  branches: BranchSummary[];
}

/** A set of users that share the same PIN — they must be given distinct PINs. */
export interface PinConflictGroup {
  users: PinConflictUser[];
}

export const userManagementService = {
  getAll: () =>
    api.get<ApiResponse<UserResponse[]>>("/users").then((r) => r.data.data),

  getById: (id: string) =>
    api.get<ApiResponse<UserResponse>>(`/users/${id}`).then((r) => r.data.data),

  create: (data: CreateUserRequest) =>
    api.post<ApiResponse<UserResponse>>("/users", data).then((r) => r.data.data),

  update: (id: string, data: UpdateUserRequest) =>
    api.put<ApiResponse<UserResponse>>(`/users/${id}`, data).then((r) => r.data.data),

  toggleStatus: (id: string) =>
    api.patch<ApiResponse<UserResponse>>(`/users/${id}/toggle-status`).then((r) => r.data.data),

  bulkSetStatus: (ids: string[], active: boolean) =>
    api.post<ApiResponse<number>>("/users/bulk-status", { ids, active }).then((r) => r.data.data),

  /** Replace a user's branch-access set + primary branch. ADMIN-only. */
  updateBranches: (id: string, data: UpdateBranchesRequest) =>
    api.put<ApiResponse<UserResponse>>(`/users/${id}/branches`, data).then((r) => r.data.data),

  /** Set a user's primary branch (must already be in their assigned set). ADMIN-only. */
  updatePrimaryBranch: (id: string, primaryBranchId: string) =>
    api
      .patch<ApiResponse<UserResponse>>(`/users/${id}/primary-branch`, { primaryBranchId })
      .then((r) => r.data.data),

  /** ADMIN resets a user's password. The user must change it on next login. */
  resetPassword: (id: string, newPassword: string) =>
    api.post<ApiResponse<void>>(`/users/${id}/reset-password`, { newPassword }).then((r) => r.data.data),

  /** Reports staff who share a PIN (pre-existing collisions). On-demand; heavy. ADMIN/MANAGER. */
  findPinConflicts: () =>
    api.get<ApiResponse<PinConflictGroup[]>>("/users/pin-conflicts").then((r) => r.data.data),
};
