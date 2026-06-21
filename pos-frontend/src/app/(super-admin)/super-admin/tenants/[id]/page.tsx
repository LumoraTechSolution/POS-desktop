'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { superAdminTenantService } from '@/services/superAdminTenantService';
import { TenantDetailResponse, TenantConfigurationRequest } from '@/types/superAdmin';
import {
  ChevronRight,
  Building2,
  CheckCircle2,
  XCircle,
  BarChart3,
  Settings2,
  Loader2,
  AlertTriangle,
  Users,
} from 'lucide-react';
import OverviewTab from './OverviewTab';
import ConfigurationTab from './ConfigurationTab';
import UsersTab from './UsersTab';

type Tab = 'overview' | 'users' | 'configuration';

export default function TenantDetailPage() {
  const params = useParams();
  const router = useRouter();
  const tenantId = params.id as string;

  const [tenant, setTenant] = useState<TenantDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [saving, setSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  const fetchTenant = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await superAdminTenantService.getTenantDetail(tenantId);
      setTenant(data);
    } catch (err: unknown) {
      console.error('Failed to fetch tenant:', err);
      const apiErr = err as { response?: { data?: { message?: string } } };
      setError(apiErr?.response?.data?.message || 'Failed to load tenant details.');
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    if (tenantId) fetchTenant();
  }, [tenantId, fetchTenant]);

  const handleSaveConfiguration = async (payload: TenantConfigurationRequest) => {
    try {
      setSaving(true);
      setSaveSuccess(false);
      const updated = await superAdminTenantService.updateTenantConfiguration(tenantId, payload);
      setTenant(updated);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 4000);
    } catch (err: unknown) {
      const apiErr = err as { response?: { data?: { message?: string } } };
      alert(apiErr?.response?.data?.message || 'Failed to save configuration.');
    } finally {
      setSaving(false);
    }
  };

  // ── Loading State ──
  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center space-y-4">
          <Loader2 className="w-10 h-10 text-blue-500 animate-spin mx-auto" />
          <p className="text-muted-foreground font-medium">Loading tenant details...</p>
        </div>
      </div>
    );
  }

  // ── Error State ──
  if (error || !tenant) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center space-y-4 max-w-md">
          <AlertTriangle className="w-12 h-12 text-warning mx-auto" />
          <h2 className="text-xl font-bold text-muted-foreground">Tenant Not Found</h2>
          <p className="text-muted-foreground">{error || 'The requested tenant could not be loaded.'}</p>
          <button
            onClick={() => router.push('/super-admin/tenants')}
            className="px-6 py-2.5 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 transition-colors"
          >
            Back to Tenants
          </button>
        </div>
      </div>
    );
  }

  const tabs: { key: Tab; label: string; icon: React.ReactNode }[] = [
    { key: 'overview', label: 'Overview', icon: <BarChart3 className="w-4 h-4" /> },
    { key: 'users', label: 'Users', icon: <Users className="w-4 h-4" /> },
    { key: 'configuration', label: 'Configuration', icon: <Settings2 className="w-4 h-4" /> },
  ];

  return (
    <div className="space-y-6">
      {/* Success Banner */}
      {saveSuccess && (
        <div className="bg-emerald-50 border border-emerald-200 text-emerald-700 px-6 py-3 rounded-xl flex items-center gap-3 font-medium animate-in fade-in slide-in-from-top-2 duration-300">
          <CheckCircle2 className="w-5 h-5 shrink-0" />
          Configuration saved successfully.
        </div>
      )}

      {/* Breadcrumbs */}
      <nav className="flex items-center gap-2 text-sm text-muted-foreground">
        <Link href="/super-admin" className="hover:text-blue-600 transition-colors">
          Dashboard
        </Link>
        <ChevronRight className="w-4 h-4" />
        <Link href="/super-admin/tenants" className="hover:text-blue-600 transition-colors">
          Tenants
        </Link>
        <ChevronRight className="w-4 h-4" />
        <span className="text-muted-foreground font-medium">{tenant.name}</span>
      </nav>

      {/* Header */}
      <div className="bg-background rounded-2xl border border-border shadow-sm p-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <div className="w-14 h-14 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl flex items-center justify-center text-white shadow-lg shadow-blue-500/20">
            <Building2 className="w-7 h-7" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-muted-foreground">{tenant.name}</h1>
            <p className="text-muted-foreground text-sm mt-0.5">
              {tenant.domain} &bull; {tenant.slug}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          {/* Plan Badge */}
          <span
            className={`px-3 py-1.5 text-xs font-bold rounded-full border ${
              tenant.planTier === 'ENTERPRISE'
                ? 'bg-amber-50 text-amber-700 border-amber-200'
                : tenant.planTier === 'MEDIUM_BUSINESS'
                ? 'bg-purple-50 text-purple-700 border-purple-200'
                : 'bg-blue-50 text-blue-700 border-blue-200'
            }`}
          >
            {tenant.planTier.replace(/_/g, ' ')}
          </span>
          {/* Status Badge */}
          {tenant.isActive ? (
            <span className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
              <CheckCircle2 className="w-3.5 h-3.5" /> Active
            </span>
          ) : (
            <span className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold rounded-full bg-rose-50 text-rose-700 border border-rose-200">
              <XCircle className="w-3.5 h-3.5" /> Suspended
            </span>
          )}
        </div>
      </div>

      {/* Tab Navigation */}
      <div className="bg-background rounded-2xl border border-border shadow-sm overflow-hidden">
        <div className="flex border-b border-border">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-2 px-6 py-4 text-sm font-semibold transition-colors relative ${
                activeTab === tab.key
                  ? 'text-blue-600'
                  : 'text-muted-foreground hover:text-muted-foreground'
              }`}
            >
              {tab.icon}
              {tab.label}
              {activeTab === tab.key && (
                <span className="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-600 rounded-full" />
              )}
            </button>
          ))}
        </div>

        {/* Tab Content */}
        <div className="p-6">
          {activeTab === 'overview' && <OverviewTab tenant={tenant} />}
          {activeTab === 'users' && <UsersTab tenantId={tenantId} />}
          {activeTab === 'configuration' && (
            <ConfigurationTab
              tenant={tenant}
              onSave={handleSaveConfiguration}
              saving={saving}
            />
          )}
        </div>
      </div>
    </div>
  );
}
