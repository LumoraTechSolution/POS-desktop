'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { superAdminAuthService } from '@/services/superAdminAuthService';
import { useSuperAdminStore } from '@/stores/superAdminStore';
import { Lock, Loader2, ShieldAlert } from 'lucide-react';

export default function ChangePasswordPage() {
  const router = useRouter();
  const logout = useSuperAdminStore((s) => s.logout);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (newPassword.length < 12) {
      setError('New password must be at least 12 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    if (newPassword === currentPassword) {
      setError('New password must differ from current password.');
      return;
    }

    setLoading(true);
    try {
      await superAdminAuthService.changePassword({ currentPassword, newPassword });
      // Backend revoked all refresh tokens; force a clean re-login.
      logout();
      try {
        await fetch('/api/super-admin-logout', { method: 'POST' });
      } catch {
        /* swallow */
      }
      router.push('/super-admin/login?changed=1');
    } catch (err: unknown) {
      setError(
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
          'Could not change password. Please try again.'
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full max-w-md bg-card border border-border rounded-2xl shadow-2xl p-8 text-foreground relative overflow-hidden">
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full h-32 bg-blue-600/20 blur-[100px] pointer-events-none" />

      <div className="flex flex-col items-center mb-8 relative">
        <div className="w-16 h-16 bg-muted border border-border rounded-2xl flex items-center justify-center mb-4 shadow-inner">
          <ShieldAlert className="w-8 h-8 text-blue-500" />
        </div>
        <h1 className="text-2xl font-bold tracking-tight">Set a new password</h1>
        <p className="text-muted-foreground mt-2 text-sm text-center">
          The default credentials are public. Rotate them now to access the platform.
        </p>
      </div>

      {error && (
        <div className="bg-destructive/10 border border-destructive/50 text-destructive p-4 rounded-xl text-sm mb-6 flex items-start gap-3">
          <Lock className="w-5 h-5 shrink-0" />
          <p>{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-5 relative">
        <div>
          <label className="block text-sm font-medium text-muted-foreground mb-2" htmlFor="current">
            Current password
          </label>
          <input
            id="current"
            name="currentPassword"
            type="password"
            required
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            className="w-full bg-muted border border-border rounded-xl px-4 py-3 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-muted-foreground mb-2" htmlFor="new">
            New password (min 12 chars)
          </label>
          <input
            id="new"
            name="newPassword"
            type="password"
            required
            minLength={12}
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            className="w-full bg-muted border border-border rounded-xl px-4 py-3 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-muted-foreground mb-2" htmlFor="confirm">
            Confirm new password
          </label>
          <input
            id="confirm"
            name="confirmPassword"
            type="password"
            required
            minLength={12}
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className="w-full bg-muted border border-border rounded-xl px-4 py-3 text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
          />
        </div>

        <button
          type="submit"
          disabled={loading}
          className="w-full bg-blue-600 hover:bg-blue-500 text-white font-medium py-3 rounded-xl transition-all flex items-center justify-center gap-2 mt-4 shadow-[0_0_20px_rgba(37,99,235,0.3)] hover:shadow-[0_0_25px_rgba(37,99,235,0.5)] active:scale-[0.98] disabled:opacity-60"
        >
          {loading ? (
            <>
              <Loader2 className="w-5 h-5 animate-spin" />
              Updating...
            </>
          ) : (
            'Update password'
          )}
        </button>
      </form>
    </div>
  );
}
