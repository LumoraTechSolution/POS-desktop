'use client';

import React from 'react';
import { TenantDetailResponse } from '@/types/superAdmin';
import {
  MapPin,
  Users,
  Package,
  ShoppingCart,
  DollarSign,
  CalendarDays,
  Clock,
  AlertTriangle,
} from 'lucide-react';
import { format } from 'date-fns';

interface Props {
  tenant: TenantDetailResponse;
}

function UsageMeter({
  label,
  icon,
  current,
  max,
  color,
}: {
  label: string;
  icon: React.ReactNode;
  current: number;
  max: number;
  color: string;
}) {
  const percentage = max > 0 ? Math.min((current / max) * 100, 100) : 0;
  const isOverLimit = current >= max;

  return (
    <div className="bg-background border border-border rounded-xl p-5 hover:shadow-md transition-shadow">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2.5">
          <div className={`w-9 h-9 rounded-lg flex items-center justify-center ${color}`}>
            {icon}
          </div>
          <span className="text-sm font-semibold text-muted-foreground">{label}</span>
        </div>
        <span className={`text-sm font-bold ${isOverLimit ? 'text-rose-600' : 'text-muted-foreground'}`}>
          {current.toLocaleString()} / {max.toLocaleString()}
        </span>
      </div>
      <div className="w-full h-2.5 bg-muted rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-700 ease-out ${
            isOverLimit
              ? 'bg-gradient-to-r from-rose-400 to-rose-600'
              : percentage > 75
              ? 'bg-gradient-to-r from-amber-400 to-amber-500'
              : 'bg-gradient-to-r from-blue-400 to-blue-600'
          }`}
          style={{ width: `${percentage}%` }}
        />
      </div>
      {isOverLimit && (
        <div className="flex items-center gap-1.5 mt-2 text-xs text-rose-600 font-medium">
          <AlertTriangle className="w-3.5 h-3.5" />
          At capacity
        </div>
      )}
    </div>
  );
}

export default function OverviewTab({ tenant }: Props) {
  const { usage } = tenant;

  const hasBusinessInfo =
    tenant.addressLine1 || tenant.addressLine2 || tenant.phone || tenant.logoUrl || tenant.receiptFooter;

  return (
    <div className="space-y-8">
      {/* Business Profile */}
      <div>
        <h3 className="text-base font-bold text-muted-foreground mb-4">Business Profile</h3>
        <div className="bg-muted border border-border rounded-xl p-5 flex gap-5">
          {tenant.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={tenant.logoUrl}
              alt={`${tenant.name} logo`}
              className="w-16 h-16 rounded-lg object-contain bg-background border border-border shrink-0"
            />
          ) : (
            <div className="w-16 h-16 rounded-lg bg-background border border-border flex items-center justify-center text-muted-foreground shrink-0">
              <MapPin className="w-6 h-6" />
            </div>
          )}
          <div className="flex-1 min-w-0 space-y-1.5 text-sm">
            {hasBusinessInfo ? (
              <>
                {(tenant.addressLine1 || tenant.addressLine2) && (
                  <p className="text-muted-foreground">
                    {[tenant.addressLine1, tenant.addressLine2].filter(Boolean).join(', ')}
                  </p>
                )}
                {tenant.phone && <p className="text-muted-foreground">{tenant.phone}</p>}
                {tenant.receiptFooter && (
                  <p className="text-xs text-muted-foreground italic">“{tenant.receiptFooter}”</p>
                )}
              </>
            ) : (
              <p className="text-muted-foreground italic">
                The tenant hasn&apos;t set their business address, phone, or logo yet.
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Resource Utilization Section */}
      <div>
        <h3 className="text-base font-bold text-muted-foreground mb-4">Resource Utilization</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <UsageMeter
            label="Branches"
            icon={<MapPin className="w-4.5 h-4.5 text-indigo-600" />}
            current={usage.activeLocations}
            max={tenant.maxLocations}
            color="bg-indigo-50"
          />
          <UsageMeter
            label="User Accounts"
            icon={<Users className="w-4.5 h-4.5 text-cyan-600" />}
            current={usage.activeUsers}
            max={tenant.maxUsers}
            color="bg-cyan-50"
          />
          <UsageMeter
            label="Product Catalog"
            icon={<Package className="w-4.5 h-4.5 text-violet-600" />}
            current={usage.totalProducts}
            max={tenant.maxProducts}
            color="bg-violet-50"
          />
        </div>
      </div>

      {/* Revenue & Activity Snapshot */}
      <div>
        <h3 className="text-base font-bold text-muted-foreground mb-4">Revenue Snapshot</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="bg-gradient-to-br from-emerald-50 to-green-50 border border-emerald-100 rounded-xl p-6">
            <div className="flex items-center gap-3 mb-2">
              <div className="w-10 h-10 bg-background rounded-lg shadow-sm flex items-center justify-center">
                <DollarSign className="w-5 h-5 text-success" />
              </div>
              <span className="text-sm font-semibold text-emerald-700">Lifetime Revenue</span>
            </div>
            <p className="text-3xl font-black text-emerald-900 tracking-tight">
              LKR {usage.lifetimeRevenue.toLocaleString('en-US', { minimumFractionDigits: 2 })}
            </p>
          </div>
          <div className="bg-gradient-to-br from-blue-50 to-indigo-50 border border-blue-100 rounded-xl p-6">
            <div className="flex items-center gap-3 mb-2">
              <div className="w-10 h-10 bg-background rounded-lg shadow-sm flex items-center justify-center">
                <ShoppingCart className="w-5 h-5 text-blue-600" />
              </div>
              <span className="text-sm font-semibold text-blue-700">Total Orders</span>
            </div>
            <p className="text-3xl font-black text-blue-900 tracking-tight">
              {usage.totalOrders.toLocaleString()}
            </p>
          </div>
        </div>
      </div>

      {/* Subscription Info */}
      <div>
        <h3 className="text-base font-bold text-muted-foreground mb-4">Subscription Details</h3>
        <div className="bg-muted border border-border rounded-xl divide-y divide-border">
          <InfoRow
            icon={<CalendarDays className="w-4 h-4 text-muted-foreground" />}
            label="Start Date"
            value={
              tenant.subscriptionStart
                ? format(new Date(tenant.subscriptionStart), 'PPP')
                : 'Not set'
            }
          />
          <InfoRow
            icon={<Clock className="w-4 h-4 text-muted-foreground" />}
            label="Expiry Date"
            value={
              tenant.subscriptionEnd
                ? format(new Date(tenant.subscriptionEnd), 'PPP')
                : 'No expiry (Unlimited)'
            }
            highlight={tenant.isSubscriptionExpired}
          />
          <InfoRow
            icon={<CalendarDays className="w-4 h-4 text-muted-foreground" />}
            label="Tenant Created"
            value={format(new Date(tenant.createdAt), 'PPP')}
          />
          {tenant.notes && (
            <div className="px-5 py-4">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1">
                Admin Notes
              </p>
              <p className="text-sm text-muted-foreground leading-relaxed">{tenant.notes}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function InfoRow({
  icon,
  label,
  value,
  highlight,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  highlight?: boolean;
}) {
  return (
    <div className="flex items-center justify-between px-5 py-4">
      <div className="flex items-center gap-2.5 text-sm text-muted-foreground">
        {icon}
        {label}
      </div>
      <span
        className={`text-sm font-semibold ${
          highlight ? 'text-rose-600' : 'text-muted-foreground'
        }`}
      >
        {value}
        {highlight && ' (Expired)'}
      </span>
    </div>
  );
}
