import api from './api';

export interface ExpenseCategory {
  id: string;
  name: string;
  isActive: boolean;
}

export interface Expense {
  id: string;
  categoryId: string;
  categoryName: string;
  amount: number;
  expenseDate: string; // ISO date (yyyy-MM-dd)
  payee?: string | null;
  paymentMethod?: string | null;
  reference?: string | null;
  notes?: string | null;
  recurring: boolean;
  recurringInterval?: string | null;
  branchId?: string | null;
  branchName?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface ExpenseRequest {
  categoryId: string;
  amount: number;
  expenseDate: string;
  payee?: string | null;
  paymentMethod?: string | null;
  reference?: string | null;
  notes?: string | null;
  recurring?: boolean;
  recurringInterval?: string | null;
  branchId?: string | null;
}

export interface ExpenseCategoryRequest {
  name: string;
  isActive: boolean;
}

/** Minimal Spring Data Page shape (only fields the UI uses). */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const expenseService = {
  // ── Expenses ──
  list: async (params?: { start?: string; end?: string; page?: number; size?: number; branchId?: string }): Promise<Page<Expense>> => {
    const res = await api.get<{ data: Page<Expense> }>('/expenses', { params });
    return res.data.data;
  },
  create: async (data: ExpenseRequest): Promise<Expense> => {
    const res = await api.post<{ data: Expense }>('/expenses', data);
    return res.data.data;
  },
  update: async (id: string, data: ExpenseRequest): Promise<Expense> => {
    const res = await api.put<{ data: Expense }>(`/expenses/${id}`, data);
    return res.data.data;
  },
  remove: async (id: string): Promise<void> => {
    await api.delete(`/expenses/${id}`);
  },

  // ── Categories ──
  listCategories: async (): Promise<ExpenseCategory[]> => {
    const res = await api.get<{ data: ExpenseCategory[] }>('/expense-categories');
    return res.data.data;
  },
  createCategory: async (data: ExpenseCategoryRequest): Promise<ExpenseCategory> => {
    const res = await api.post<{ data: ExpenseCategory }>('/expense-categories', data);
    return res.data.data;
  },
  updateCategory: async (id: string, data: ExpenseCategoryRequest): Promise<ExpenseCategory> => {
    const res = await api.put<{ data: ExpenseCategory }>(`/expense-categories/${id}`, data);
    return res.data.data;
  },
  removeCategory: async (id: string): Promise<void> => {
    await api.delete(`/expense-categories/${id}`);
  },
};
