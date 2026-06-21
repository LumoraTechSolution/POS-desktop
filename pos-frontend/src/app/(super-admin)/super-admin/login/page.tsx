'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { superAdminAuthService } from '@/services/superAdminAuthService';
import { useSuperAdminStore } from '@/stores/superAdminStore';
import { ShieldAlert, Loader2, Lock } from 'lucide-react';

export default function SuperAdminLoginPage() {
  const router = useRouter();
  const setAuth = useSuperAdminStore((state) => state.setAuth);
  
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await superAdminAuthService.login({ email, password });
      setAuth(response.superAdmin, response.accessToken, response.refreshToken);
      if (response.passwordChangeRequired) {
        router.push('/super-admin/change-password');
      } else {
        router.push('/super-admin');
      }
    } catch (err: unknown) {
      setError((err as { response?: { data?: { message?: string } } })?.response?.data?.message || 'Access Denied: Invalid Credentials');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md bg-card border border-border rounded-2xl shadow-2xl p-8 text-foreground relative overflow-hidden">
      {/* Subtle background glow */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full h-32 bg-blue-600/20 blur-[100px] pointer-events-none" />

      <div className="flex flex-col items-center mb-10 relative">
        <div className="w-16 h-16 bg-muted border border-border rounded-2xl flex items-center justify-center mb-4 shadow-inner">
          <ShieldAlert className="w-8 h-8 text-blue-500" />
        </div>
        <h1 className="text-2xl font-bold tracking-tight">Lumora Super Admin</h1>
        <p className="text-muted-foreground mt-2 text-sm">Restricted access protocol</p>
      </div>

      {error && (
        <div className="bg-destructive/10 border border-destructive/50 text-destructive p-4 rounded-xl text-sm mb-6 flex items-start gap-3">
          <Lock className="w-5 h-5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      <form onSubmit={handleLogin} className="space-y-5 relative">
        <div>
          <label className="block text-sm font-medium text-muted-foreground mb-2" htmlFor="email">
            Admin Identifier
          </label>
          <input
            id="email"
            type="email"
            required
            autoComplete="username"
            className="w-full bg-muted border border-border rounded-xl px-4 py-3 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
            placeholder="superadmin@lumora.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-muted-foreground mb-2" htmlFor="password">
            Security Key
          </label>
          <input
            id="password"
            type="password"
            required
            autoComplete="current-password"
            className="w-full bg-muted border border-border rounded-xl px-4 py-3 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full bg-blue-600 hover:bg-blue-500 text-white font-medium py-3 rounded-xl transition-all flex items-center justify-center gap-2 mt-4 shadow-[0_0_20px_rgba(37,99,235,0.3)] hover:shadow-[0_0_25px_rgba(37,99,235,0.5)] active:scale-[0.98]"
        >
          {loading ? (
            <>
              <Loader2 className="w-5 h-5 animate-spin" />
              Authenticating...
            </>
          ) : (
            'Authorize Access'
          )}
        </button>
      </form>
    </div>
  );
}
