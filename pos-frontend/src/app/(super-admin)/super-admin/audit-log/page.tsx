'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { Search, Activity, ChevronRight, ChevronLeft } from 'lucide-react';
import { superAdminAuditService, SuperAdminAuditResponse, PagedAuditResponse } from '@/services/superAdminAuditService';
import { format } from 'date-fns';

export default function AuditLogPage() {
  const [logs, setLogs] = useState<SuperAdminAuditResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [pageData, setPageData] = useState<PagedAuditResponse | null>(null);

  // Pagination and filter states
  const [page, setPage] = useState(0);
  const [size] = useState(30);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  // Debounce search
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
      setPage(0); // Reset to page 0 on search
    }, 500);
    return () => clearTimeout(timer);
  }, [search]);

  // Handle date changes
  useEffect(() => {
    setPage(0);
  }, [startDate, endDate]);

  const fetchLogs = useCallback(async (pageNumber: number, searchTerm: string, start: string, end: string) => {
    try {
      setLoading(true);
      setError(null);
      
      // Convert to ISO if present
      const startIso = start ? new Date(start).toISOString().split('.')[0] : undefined;
      const endIso = end ? new Date(end).toISOString().split('.')[0] : undefined;

      const data = await superAdminAuditService.getGlobalAuditLogs(pageNumber, size, searchTerm, startIso, endIso);
      setLogs(data.content);
      setPageData(data);
    } catch (err: unknown) {
      console.error('Failed to fetch audit logs:', err);
      const apiErr = err as { response?: { data?: { message?: string } } };
      setError(apiErr?.response?.data?.message || 'Failed to load audit logs.');
    } finally {
      setLoading(false);
    }
  }, [size]);

  useEffect(() => {
    fetchLogs(page, debouncedSearch, startDate, endDate);
  }, [page, debouncedSearch, startDate, endDate, fetchLogs]);

  const handleNextPage = () => {
    if (pageData && !pageData.last) setPage(p => p + 1);
  };

  const handlePrevPage = () => {
    if (page > 0) setPage(p => p - 1);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-muted-foreground border-b-4 border-indigo-500 inline-block pb-1">Platform Audit Log</h1>
          <p className="text-sm text-muted-foreground mt-2">
            Centralized security trail tracking system-wide activity across all tenants.
          </p>
        </div>
        <div className="flex flex-col sm:flex-row items-end sm:items-center gap-3">
          <div className="flex items-center gap-2 bg-background px-3 py-1.5 rounded-xl border border-border shadow-sm transition-all focus-within:ring-2 focus-within:ring-indigo-500/10 focus-within:border-indigo-500">
            <div className="flex flex-col">
              <label className="text-[10px] font-bold text-muted-foreground uppercase tracking-tighter">From</label>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="bg-transparent text-sm text-muted-foreground outline-none h-6 w-32"
              />
            </div>
            <div className="w-[1px] h-8 bg-muted flex-shrink-0" />
            <div className="flex flex-col">
              <label className="text-[10px] font-bold text-muted-foreground uppercase tracking-tighter">To</label>
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="bg-transparent text-sm text-muted-foreground outline-none h-6 w-32"
              />
            </div>
            {(startDate || endDate) && (
              <button 
                onClick={() => { setStartDate(''); setEndDate(''); }}
                className="ml-1 p-1 hover:bg-muted rounded text-muted-foreground transition-colors"
                title="Clear date filter"
              >
                <Activity className="w-3.5 h-3.5 rotate-45" /> {/* Just using as a clear-ish icon */}
              </button>
            )}
          </div>

          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Filter activities..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-10 pr-4 py-2 bg-background border border-border rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all w-full sm:w-52 shadow-sm"
            />
          </div>

          <div className="flex items-center gap-3 bg-background p-2 rounded-lg border border-border shadow-sm shrink-0">
            <Activity className="w-5 h-5 text-indigo-500 ml-2" />
            <div className="text-sm font-medium pr-3 text-muted-foreground">
              {pageData?.totalElements?.toLocaleString() || 0} Events
            </div>
          </div>
        </div>
      </div>

      {/* Main Container */}
      <div className="bg-background rounded-2xl border border-border shadow-sm overflow-hidden flex flex-col h-[calc(100vh-140px)]">
        {/* Table Area */}
        <div className="flex-1 overflow-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-muted sticky top-0 z-10 border-b border-border">
              <tr>
                <th className="px-6 py-4 text-xs font-bold text-muted-foreground uppercase tracking-wider">Timestamp</th>
                <th className="px-6 py-4 text-xs font-bold text-muted-foreground uppercase tracking-wider">Tenant</th>
                <th className="px-6 py-4 text-xs font-bold text-muted-foreground uppercase tracking-wider">Action</th>
                <th className="px-6 py-4 text-xs font-bold text-muted-foreground uppercase tracking-wider">Target Entity</th>
                <th className="px-6 py-4 text-xs font-bold text-muted-foreground uppercase tracking-wider">Context</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border bg-background">
              {loading && logs.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-muted-foreground">
                    <div className="flex flex-col items-center gap-2 relative">
                      <div className="w-8 h-8 relative">
                          <div className="absolute inset-0 border-4 border-border rounded-full"></div>
                          <div className="absolute inset-0 border-4 border-indigo-500 rounded-full border-t-transparent animate-spin"></div>
                      </div>
                      Loading audit trail...
                    </div>
                  </td>
                </tr>
              ) : error ? (
                <tr>
                  <td colSpan={5} className="px-6 py-8 text-center text-rose-500">{error}</td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-muted-foreground">
                    No activity found in the system.
                  </td>
                </tr>
              ) : (
                logs.map((log) => (
                  <tr key={log.id} className="hover:bg-muted transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm font-semibold text-muted-foreground">{format(new Date(log.createdAt), 'MMM dd, yyyy')}</div>
                      <div className="text-xs text-muted-foreground">{format(new Date(log.createdAt), 'HH:mm:ss a')}</div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <div className="w-8 h-8 rounded bg-gradient-to-br from-indigo-100 to-blue-100 text-indigo-700 font-bold flex items-center justify-center shrink-0">
                          {log.tenantName.substring(0, 1).toUpperCase()}
                        </div>
                        <div>
                          <div className="text-sm font-semibold text-muted-foreground">{log.tenantName}</div>
                          <div className="text-xs text-muted-foreground font-mono" title={log.tenantId}>{log.tenantId.substring(0, 8)}...</div>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className={`inline-flex px-3 py-1 text-[11px] font-bold rounded-full uppercase tracking-wider border ${
                        log.action.includes('CREATE') ? 'bg-emerald-50 text-emerald-700 border-emerald-200' :
                        log.action.includes('UPDATE') ? 'bg-blue-50 text-blue-700 border-blue-200' :
                        log.action.includes('DELETE') ? 'bg-rose-50 text-rose-700 border-rose-200' :
                        log.action.includes('LOGIN') ? 'bg-amber-50 text-amber-700 border-amber-200' :
                        'bg-muted text-muted-foreground border-border'
                      }`}>
                        {log.action}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <div className="text-sm font-semibold text-muted-foreground">{log.entityType}</div>
                      {log.entityId && (
                        <div className="text-xs text-muted-foreground font-mono mt-0.5">ID: {log.entityId.substring(0, 8)}...</div>
                      )}
                    </td>
                    <td className="px-6 py-4 max-w-xs truncate">
                      <div className="text-xs text-muted-foreground">
                        <span className="font-semibold text-muted-foreground block mb-0.5">IP: {log.ipAddress || 'System'}</span>
                        <span className="truncate inline-block w-full" title={log.userId || 'System'}>
                          User: {log.userId || 'System Action'}
                        </span>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Footer / Pagination */}
        {pageData && pageData.totalPages > 1 && (
          <div className="bg-muted px-6 py-4 border-t border-border flex items-center justify-between shadow-[0_-4px_6px_-1px_rgb(0,0,0,0.05)] z-20">
            <span className="text-sm text-muted-foreground font-medium tracking-wide">
              Showing {(page * size) + 1} to {Math.min((page + 1) * size, pageData.totalElements)} of {pageData.totalElements} events
            </span>
            <div className="flex gap-2">
              <button
                onClick={handlePrevPage}
                disabled={page === 0}
                className="px-4 py-2 border rounded-lg text-sm font-medium hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center gap-1 bg-background shadow-sm"
              >
                <ChevronLeft className="w-4 h-4" /> Previous
              </button>
              <button
                onClick={handleNextPage}
                disabled={pageData.last}
                className="px-4 py-2 border rounded-lg text-sm font-medium hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center gap-1 bg-background shadow-sm"
              >
                Next <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
