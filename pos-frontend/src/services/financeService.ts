import api from './api';

export interface CategoryAmount {
  categoryId: string;
  categoryName: string;
  amount: number;
}

export interface ProfitLossReport {
  periodStart: string;
  periodEnd: string;
  revenue: number;
  costOfGoodsSold: number;
  grossProfit: number;
  grossMarginPct: number;
  operatingExpenses: number;
  netProfit: number;
  netMarginPct: number;
  expenseBreakdown: CategoryAmount[];
}

export interface CashFlowPeriod {
  label: string;
  inflow: number;
  outflow: number;
  net: number;
}

export interface CashFlowReport {
  periodStart: string;
  periodEnd: string;
  totalInflow: number;
  totalOutflow: number;
  netCashFlow: number;
  avgMonthlyNetBurn: number | null;
  runwayMonths: number | null;
  series: CashFlowPeriod[];
}

export const financeService = {
  getProfitLoss: async (start: string, end: string, branchId?: string): Promise<ProfitLossReport> => {
    const res = await api.get<{ data: ProfitLossReport }>('/finance/profit-loss', {
      params: { start, end, ...(branchId ? { branchId } : {}) },
    });
    return res.data.data;
  },
  getCashFlow: async (start: string, end: string, branchId?: string): Promise<CashFlowReport> => {
    const res = await api.get<{ data: CashFlowReport }>('/finance/cash-flow', {
      params: { start, end, ...(branchId ? { branchId } : {}) },
    });
    return res.data.data;
  },
};
