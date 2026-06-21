'use client';

import React, { useState } from 'react';
import { superAdminTenantService } from '@/services/superAdminTenantService';
import { X, Loader2, Building2, User, KeyRound } from 'lucide-react';
import { PlanTier } from '@/types/superAdmin';

interface ProvisionTenantModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function ProvisionTenantModal({ isOpen, onClose, onSuccess }: ProvisionTenantModalProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Form State
  const [name, setName] = useState('');
  const [domain, setDomain] = useState('');
  const [planTier, setPlanTier] = useState<PlanTier>('SMALL_BUSINESS');
  const [adminEmail, setAdminEmail] = useState('');
  const [adminFirstName, setAdminFirstName] = useState('');
  const [adminLastName, setAdminLastName] = useState('');
  const [adminPassword, setAdminPassword] = useState('');

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      await superAdminTenantService.createTenant({
        name,
        domain,
        planTier,
        adminEmail,
        adminFirstName,
        adminLastName,
        adminPassword,
      });
      onSuccess();
      // Reset form on success
      setName(''); setDomain(''); setPlanTier('SMALL_BUSINESS');
      setAdminEmail(''); setAdminFirstName(''); setAdminLastName(''); setAdminPassword('');
    } catch (err: unknown) {
      setError((err as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Failed to provision tenant.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="absolute inset-0 bg-card/40 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />
      
      {/* Modal panel */}
      <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-2xl max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-200">
        
        {/* Header */}
        <div className="px-6 py-4 border-b border-border flex items-center justify-between shrink-0 bg-muted/50">
          <div>
            <h2 className="text-xl font-bold text-muted-foreground">Provision New Tenant</h2>
            <p className="text-sm text-muted-foreground mt-0.5">Automated workspace and database setup</p>
          </div>
          <button 
            onClick={onClose}
            className="p-2 text-muted-foreground hover:text-muted-foreground hover:bg-muted rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Form Body */}
        <div className="p-6 overflow-y-auto">
          {error && (
            <div className="bg-red-50 text-destructive p-4 rounded-xl text-sm mb-6 border border-red-100">
              {error}
            </div>
          )}

          <form id="provision-form" onSubmit={handleSubmit} className="space-y-8">
            
            {/* Business Info Section */}
            <section>
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-4 flex items-center gap-2 border-b pb-2">
                <Building2 className="w-4 h-4" /> 1. Business Workspace
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-muted-foreground mb-1">Company Name *</label>
                  <input
                    type="text"
                    required
                    className="w-full px-4 py-2.5 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow"
                    placeholder="e.g. Acme Retail"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-muted-foreground mb-1">Tenant Slug (Domain) *</label>
                  <div className="flex">
                    <input
                      type="text"
                      required
                      className="w-full px-4 py-2.5 border border-border rounded-l-lg focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow uppercase font-mono text-sm"
                      placeholder="ACME"
                      value={domain}
                      onChange={(e) => setDomain(e.target.value.toUpperCase().replace(/[^A-Z0-9-]/g, ''))}
                    />
                    <div className="px-4 py-2.5 border border-l-0 border-border bg-muted rounded-r-lg text-muted-foreground font-mono text-sm flex items-center">
                      .lumora.com
                    </div>
                  </div>
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-sm font-medium text-muted-foreground mb-1">Subscription Plan Tier *</label>
                  <select
                    className="w-full px-4 py-2.5 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 transition-shadow bg-background"
                    value={planTier}
                    onChange={(e) => setPlanTier(e.target.value as PlanTier)}
                  >
                    <option value="SMALL_BUSINESS">Small Business (5 Users, 1 Location, Base POS)</option>
                    <option value="MEDIUM_BUSINESS">Medium Business (15 Users, 3 Locations, Core+PO+Tax)</option>
                    <option value="ENTERPRISE">Enterprise (Unlimited, All Features)</option>
                  </select>
                </div>
              </div>
            </section>

            {/* Admin Profile Section */}
            <section>
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-4 flex items-center gap-2 border-b pb-2">
                <User className="w-4 h-4" /> 2. SuperUser Account
              </h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-muted-foreground mb-1">First Name *</label>
                  <input
                    type="text"
                    required
                    className="w-full px-4 py-2.5 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="Jane"
                    value={adminFirstName}
                    onChange={(e) => setAdminFirstName(e.target.value)}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-muted-foreground mb-1">Last Name *</label>
                  <input
                    type="text"
                    required
                    className="w-full px-4 py-2.5 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="Smith"
                    value={adminLastName}
                    onChange={(e) => setAdminLastName(e.target.value)}
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-sm font-medium text-muted-foreground mb-1 flex items-center gap-1.5">
                    Email Address * <span className="text-xs text-muted-foreground font-normal">(Used for primary login)</span>
                  </label>
                  <input
                    type="email"
                    required
                    className="w-full px-4 py-2.5 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="admin@acme.com"
                    value={adminEmail}
                    onChange={(e) => setAdminEmail(e.target.value)}
                  />
                </div>
              </div>
            </section>

            {/* Security Section */}
            <section>
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-4 flex items-center gap-2 border-b pb-2">
                <KeyRound className="w-4 h-4" /> 3. Initial Security
              </h3>
              <div>
                <label className="block text-sm font-medium text-muted-foreground mb-1">Temporary Password *</label>
                <input
                  type="password"
                  required
                  className="w-full px-4 py-2.5 border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="At least 8 characters..."
                  minLength={6}
                  value={adminPassword}
                  onChange={(e) => setAdminPassword(e.target.value)}
                />
                <p className="text-xs text-muted-foreground mt-1.5">The tenant admin should be forced to change this on first login (coming in v2.0).</p>
              </div>
            </section>

          </form>
        </div>

        {/* Footer Actions */}
        <div className="p-6 border-t border-border bg-muted flex items-center justify-end gap-3 shrink-0">
          <button
            type="button"
            className="px-6 py-2.5 text-sm font-medium text-muted-foreground hover:bg-muted rounded-xl transition-colors"
            onClick={onClose}
            disabled={loading}
          >
            Cancel
          </button>
          <button
            type="submit"
            form="provision-form"
            disabled={loading}
            className="px-6 py-2.5 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-xl shadow-sm transition-all focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 flex items-center gap-2"
          >
            {loading && <Loader2 className="w-4 h-4 animate-spin" />}
            {loading ? 'Executing Provisioning...' : 'Provision Tenant Gateway'}
          </button>
        </div>
      </div>
    </div>
  );
}
