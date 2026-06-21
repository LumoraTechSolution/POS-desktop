import api from "./api";
import { ApiResponse, Page as PageResponse } from "@/types/common";

export interface LoyaltyTransaction {
  id: string;
  /** EARN, REDEEM or ADJUST. */
  type: "EARN" | "REDEEM" | "ADJUST";
  /** Signed points delta (positive earned, negative redeemed). */
  points: number;
  /** Running balance after this entry. */
  balanceAfter: number;
  description?: string | null;
  saleId?: string | null;
  createdAt: string;
}

export const loyaltyService = {
  /** A customer's loyalty points ledger (earn/redeem history), newest first. */
  getLedger: (customerId: string, page = 0, size = 20) =>
    api
      .get<ApiResponse<PageResponse<LoyaltyTransaction>>>(
        `/loyalty/customers/${customerId}?page=${page}&size=${size}`
      )
      .then((res) => res.data.data),
};
