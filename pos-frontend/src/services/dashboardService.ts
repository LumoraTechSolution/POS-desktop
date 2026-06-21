import api from "./api";
import { DashboardData } from "@/types/dashboard";
import { ApiResponse } from "@/types/common";

/**
 * Dashboard analytics service.
 * Fetches all dashboard data in a single API call.
 */
export const dashboardService = {
  /**
   * Get complete dashboard analytics data.
   * Includes KPIs, sales trend, top products, payment breakdown,
   * low stock alerts, and recent transactions.
   */
  getDashboard: () =>
    api.get<ApiResponse<DashboardData>>("/dashboard").then(res => res.data.data),
};
