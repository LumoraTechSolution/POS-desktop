import api from './api';

export interface TimeRecord {
  id: string;
  userId: string;
  userName: string;
  userRole: string;
  clockInTime: string;
  clockOutTime: string | null;
  notes: string | null;
  durationMinutes: number | null;
}

export interface PagedTimeRecords {
  content: TimeRecord[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const timeClockService = {
  clockIn: async () => {
    const response = await api.post<{ data: TimeRecord }>('/time-clock/clock-in');
    return response.data.data;
  },

  clockOut: async (notes?: string) => {
    const response = await api.post<{ data: TimeRecord }>('/time-clock/clock-out', null, {
      params: { notes }
    });
    return response.data.data;
  },

  getStatus: async () => {
    const response = await api.get<{ data: TimeRecord | null }>('/time-clock/status');
    return response.data.data;
  },

  getHistory: async (page = 0, size = 10) => {
    const response = await api.get<{ data: PagedTimeRecords }>(`/time-clock/history`, {
      params: { page, size }
    });
    return response.data.data;
  },

  getAllHistory: async (page = 0, size = 10, filters?: {
    from?: string;
    to?: string;
    status?: string;
    search?: string;
  }) => {
    const response = await api.get<{ data: PagedTimeRecords }>(`/time-clock/all-history`, {
      params: { page, size, ...filters }
    });
    return response.data.data;
  }
};
