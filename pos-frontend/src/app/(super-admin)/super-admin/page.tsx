'use client';

import React, { useEffect, useState } from 'react';
import { useSuperAdminStore } from '@/stores/superAdminStore';
import { ShieldCheck, Users, Building2, CreditCard, DollarSign } from 'lucide-react';
import superAdminApi from '@/services/superAdminApi';
import { ApiResponse } from '@/types/common';
import { PlatformStatsResponse } from '@/types/superAdmin';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';

export default function SuperAdminDashboard() {
  const { user } = useSuperAdminStore();
  const [stats, setStats] = useState<PlatformStatsResponse | null>(null);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const res = await superAdminApi.get<ApiResponse<PlatformStatsResponse>>('/stats');
        setStats(res.data.data);
      } catch (err) {
        console.error('Failed to fetch platform stats:', err);
      }
    };
    fetchStats();
  }, []);

  if (!user) return null;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4 bg-background p-6 rounded-xl border border-border shadow-sm">
        <div className="w-16 h-16 bg-blue-50 text-blue-600 border border-blue-100 rounded-full flex items-center justify-center shrink-0">
          <ShieldCheck className="w-8 h-8" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-muted-foreground">Welcome back, {user.firstName}</h1>
          <p className="text-muted-foreground mt-1">
            Super Admin Access Level • {user.email}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-6">
        <StatCard 
          title="Projected MRR" 
          value={`LKR ${(stats?.projectedMrr ?? 0).toLocaleString()}`} 
          icon={<DollarSign className="w-6 h-6 text-success" />}
          gradient="from-emerald-500/10 to-transparent border-emerald-100"
        />
        <StatCard 
          title="Active Tenants" 
          value={stats?.activeTenants ?? '-'} 
          icon={<Building2 className="w-6 h-6 text-indigo-500" />}
          gradient="from-indigo-500/10 to-transparent border-indigo-100"
        />
        <StatCard 
          title="Enterprise Customers" 
          value={stats?.enterpriseCount ?? '-'} 
          icon={<ShieldCheck className="w-6 h-6 text-blue-500" />}
          gradient="from-blue-500/10 to-transparent border-blue-100"
        />
        <StatCard 
          title="Suspended Tenants" 
          value={stats?.suspendedTenants ?? '-'} 
          icon={<Users className="w-6 h-6 text-rose-500" />}
          gradient="from-rose-500/10 to-transparent border-rose-100"
        />
        <StatCard 
          title="Expired Subscriptions" 
          value={stats?.expiredSubscriptions ?? '-'} 
          icon={<CreditCard className="w-6 h-6 text-warning" />}
          gradient="from-amber-500/10 to-transparent border-amber-100"
        />
      </div>

      {/* Breakdown Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mt-8">
        <div className="bg-background p-8 border border-border rounded-2xl shadow-sm">
          <h3 className="text-lg font-bold text-muted-foreground mb-6 border-b pb-4">Tier Distribution</h3>
          <div className="h-64">
            {stats ? (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={[
                      { name: 'Small Business', value: stats.smallBusinessCount },
                      { name: 'Medium Business', value: stats.mediumBusinessCount },
                      { name: 'Enterprise', value: stats.enterpriseCount }
                    ]}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={90}
                    paddingAngle={5}
                    dataKey="value"
                  >
                    <Cell fill="#3b82f6" /> {/* blue-500 */}
                    <Cell fill="#a855f7" /> {/* purple-500 */}
                    <Cell fill="#f59e0b" /> {/* amber-500 */}
                  </Pie>
                  <Tooltip 
                    formatter={(value: number | string | undefined) => [`${value} Tenants`, 'Count']}
                    contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1)' }}
                  />
                  <Legend 
                    verticalAlign="bottom" 
                    height={36}
                    iconType="circle"
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="w-full h-full flex items-center justify-center text-muted-foreground">Loading chart...</div>
            )}
          </div>
        </div>
        
        <div className="bg-background p-8 border border-border rounded-2xl shadow-sm">
          <h3 className="text-lg font-bold text-muted-foreground mb-6 border-b pb-4">Revenue Opportunities</h3>
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-gradient-to-r from-amber-50 to-orange-50 border border-amber-100">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-background rounded-full shadow-sm">
                  <CreditCard className="w-5 h-5 text-warning" />
                </div>
                <div>
                  <h4 className="font-semibold text-amber-900">Expired Subscriptions</h4>
                  <p className="text-sm text-amber-700">{stats?.expiredSubscriptions || 0} subscriptions pending renewal</p>
                </div>
              </div>
            </div>
            
            <div className="p-4 rounded-xl bg-gradient-to-r from-blue-50 to-indigo-50 border border-blue-100">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-background rounded-full shadow-sm">
                  <Building2 className="w-5 h-5 text-blue-600" />
                </div>
                <div>
                  <h4 className="font-semibold text-blue-900">Upgrade Potential</h4>
                  <p className="text-sm text-blue-700">{stats?.smallBusinessCount || 0} Small Businesses to target for Medium Tier</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ title, value, icon, gradient }: { title: string, value: string | number, icon: React.ReactNode, gradient: string }) {
  return (
    <div className={`bg-background p-6 rounded-2xl border ${gradient} shadow-sm flex items-start justify-between relative overflow-hidden group`}>
      <div className={`absolute top-0 right-0 w-32 h-32 bg-gradient-to-bl ${gradient} opacity-50 -translate-y-8 translate-x-8 rounded-full blur-2xl group-hover:scale-110 transition-transform`} />
      <div className="relative">
        <p className="text-sm font-semibold text-muted-foreground">{title}</p>
        <p className="text-3xl font-black text-muted-foreground mt-2 tracking-tight">{value}</p>
      </div>
      <div className="w-12 h-12 bg-background rounded-xl shadow-sm flex items-center justify-center border border-border relative z-10">
        {icon}
      </div>
    </div>
  );
}
