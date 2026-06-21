'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { superAdminTenantService } from '@/services/superAdminTenantService';
import { PagedTenantResponse, TenantSummaryResponse } from '@/types/superAdmin';
import { 
  Building2,
  Search,
  SlidersHorizontal,
  ShieldAlert,
  CheckCircle2, 
  XCircle, 
  ChevronLeft, 
  ChevronRight,
  Package,
  Users,
  MapPin
} from 'lucide-react';
import { format } from 'date-fns';

import ProvisionTenantModal from './ProvisionTenantModal';

export default function SuperAdminTenantsPage() {
  const router = useRouter();
  const [data, setData] = useState<PagedTenantResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterActive, setFilterActive] = useState<boolean | undefined>(undefined);
  const [isProvisionModalOpen, setIsProvisionModalOpen] = useState(false);

  const loadTenants = useCallback(async () => {
    try {
      setLoading(true);
      const res = await superAdminTenantService.listTenants(page, 10, searchTerm || undefined, filterActive);
      setData(res);
    } catch (err) {
      console.error('Failed to load tenants', err);
    } finally {
      setLoading(false);
    }
  }, [page, searchTerm, filterActive]);

  useEffect(() => {
    loadTenants();
  }, [loadTenants]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0); // Reset to first page
    loadTenants();
  };

  const handleToggleStatus = async (tenantId: string, currentActive: boolean) => {
    if (!confirm(`Are you sure you want to ${currentActive ? 'suspend' : 'activate'} this tenant?`)) return;

    try {
      setLoading(true);
      await superAdminTenantService.toggleTenantStatus(tenantId, currentActive ? 'suspend' : 'activate');
      await loadTenants(); // Reload the data
    } catch (err) {
      console.error('Failed to update tenant status', err);
      alert('Failed to update tenant status');
    } finally {
      setLoading(false);
    }
  };

  const renderPlanBadge = (plan: string) => {
    switch (plan) {
      case 'SMALL_BUSINESS':
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-blue-50 text-blue-700 border border-blue-200">Small Business</span>;
      case 'MEDIUM_BUSINESS':
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-purple-50 text-purple-700 border border-purple-200">Medium Business</span>;
      case 'ENTERPRISE':
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-amber-50 text-amber-700 border border-amber-200 flex items-center gap-1 w-fit"><ShieldAlert className="w-3 h-3"/> Enterprise</span>;
      default:
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-muted text-muted-foreground border border-border">{plan}</span>;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-muted-foreground flex items-center gap-2">
            <Building2 className="w-6 h-6 text-muted-foreground" />
            Registered Tenants
          </h1>
          <p className="text-sm text-muted-foreground mt-1">Manage partner subscriptions and platform access</p>
        </div>
        <button 
          onClick={() => setIsProvisionModalOpen(true)}
          className="bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg font-medium shadow-sm transition-colors w-full sm:w-auto"
        >
          + Provision New Tenant
        </button>
      </div>

      <ProvisionTenantModal 
        isOpen={isProvisionModalOpen} 
        onClose={() => setIsProvisionModalOpen(false)} 
        onSuccess={() => {
          setIsProvisionModalOpen(false);
          setPage(0);
          loadTenants();
        }}
      />

      <div className="bg-background rounded-2xl shadow-sm border border-border overflow-hidden flex flex-col">
        {/* Filters Area */}
        <div className="p-4 border-b border-border flex flex-col sm:flex-row gap-4 bg-muted/50">
          <form onSubmit={handleSearch} className="relative flex-1">
            <Search className="w-5 h-5 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input 
              type="text" 
              placeholder="Search by company name or domain..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full pl-10 pr-4 py-2 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            />
          </form>
          <select 
            className="border border-border rounded-lg px-4 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 bg-background"
            value={filterActive === undefined ? 'ALL' : filterActive ? 'ACTIVE' : 'SUSPENDED'}
            onChange={(e) => {
              const val = e.target.value;
              if (val === 'ALL') setFilterActive(undefined);
              else setFilterActive(val === 'ACTIVE');
              setPage(0);
            }}
          >
            <option value="ALL">All Statuses</option>
            <option value="ACTIVE">Active Only</option>
            <option value="SUSPENDED">Suspended Only</option>
          </select>
        </div>

        {/* Table Area */}
        <div className="overflow-x-auto min-h-[400px]">
          <table className="w-full text-left text-sm whitespace-nowrap">
            <thead className="bg-muted text-muted-foreground font-medium border-b border-border">
              <tr>
                <th className="px-6 py-4">Tenant / Domain</th>
                <th className="px-6 py-4">Subscription Plan</th>
                <th className="px-6 py-4">Resource Limits</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-muted-foreground font-medium">
                    Loading tenants securely...
                  </td>
                </tr>
              ) : data?.content.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-muted-foreground font-medium">
                    No tenants match your search parameters.
                  </td>
                </tr>
              ) : (
                data?.content.map((t: TenantSummaryResponse) => (
                  <tr key={t.id} className="hover:bg-muted/60 transition-colors">
                    <td className="px-6 py-4">
                      <div className="font-semibold text-muted-foreground">{t.name}</div>
                      <div className="text-muted-foreground text-xs mt-0.5">{t.domain}</div>
                      <div className="text-muted-foreground text-xs mt-1">Joined {format(new Date(t.createdAt), 'MMM yyyy')}</div>
                    </td>
                    <td className="px-6 py-4">
                      {renderPlanBadge(t.planTier)}
                      {t.isSubscriptionExpired && <div className="text-xs text-destructive mt-1 font-medium">Expired {t.subscriptionEnd && format(new Date(t.subscriptionEnd), 'PP')}</div>}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex flex-col gap-1.5 text-xs text-muted-foreground">
                        <div className="flex items-center gap-2"><MapPin className="w-3.5 h-3.5 text-muted-foreground"/> {t.maxLocations} Locations</div>
                        <div className="flex items-center gap-2"><Users className="w-3.5 h-3.5 text-muted-foreground"/> {t.maxUsers} Users</div>
                        <div className="flex items-center gap-2"><Package className="w-3.5 h-3.5 text-muted-foreground"/> {t.maxProducts} Catalog</div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleToggleStatus(t.id, t.isActive);
                        }}
                        disabled={loading}
                        className={`group px-3 py-1.5 rounded-full flex items-center gap-2 font-bold text-xs border transition-all shadow-sm ${
                          t.isActive 
                            ? 'bg-success/10 text-success border-success/30 hover:bg-success/20 hover:text-success' 
                            : 'bg-rose-500/10 text-rose-500 border-rose-500/30 hover:bg-rose-500/20 hover:text-rose-400 animate-pulse-subtle'
                        } ${loading ? 'opacity-50 cursor-not-allowed' : ''}`}
                        title={t.isActive ? "Click to Suspend" : "Click to Reactivate"}
                      >
                        {t.isActive ? (
                          <>
                            <CheckCircle2 className="w-3.5 h-3.5" /> 
                            <span className="uppercase tracking-wider">Active</span>
                            <span className="hidden group-hover:inline ml-1 text-[10px]">— Suspend?</span>
                          </>
                        ) : (
                          <>
                            <XCircle className="w-3.5 h-3.5" /> 
                            <span className="uppercase tracking-wider">Suspended</span>
                            <span className="hidden group-hover:inline ml-1 text-[10px]">— Restore?</span>
                          </>
                        )}
                      </button>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => router.push(`/super-admin/tenants/${t.id}`)}
                        aria-label={`View configuration for ${t.name}`}
                        title="View configuration"
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-primary hover:bg-primary/10 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <SlidersHorizontal className="w-4 h-4" />
                        View Config
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Controls */}
        {data && data.totalPages > 1 && (
          <div className="p-4 border-t border-border flex items-center justify-between bg-background text-sm">
            <div className="text-muted-foreground">
              Showing page <span className="font-medium text-muted-foreground">{data.page + 1}</span> of <span className="font-medium text-muted-foreground">{data.totalPages}</span> ({data.totalElements} total tenants)
            </div>
            <div className="flex items-center gap-2">
              <button 
                disabled={data.first} 
                onClick={() => setPage((p: number) => p - 1)}
                className="p-2 hover:bg-muted rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                aria-label="Previous page"
              >
                <ChevronLeft className="w-5 h-5 text-muted-foreground" />
              </button>
              <button 
                disabled={data.last} 
                onClick={() => setPage((p: number) => p + 1)}
                className="p-2 hover:bg-muted rounded-lg disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                aria-label="Next page"
              >
                <ChevronRight className="w-5 h-5 text-muted-foreground" />
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
