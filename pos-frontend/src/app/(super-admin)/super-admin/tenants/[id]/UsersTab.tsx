'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { superAdminTenantService } from '@/services/superAdminTenantService';
import { TenantUserResponse } from '@/types/superAdmin';
import { Loader2, Mail, Phone, KeyRound, ShieldAlert, CheckCircle2, XCircle, Eye, EyeOff } from 'lucide-react';
import { format } from 'date-fns';

interface Props {
  tenantId: string;
}

export default function UsersTab({ tenantId }: Props) {
  const [users, setUsers] = useState<TenantUserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [resetting, setResetting] = useState<TenantUserResponse | null>(null);

  const fetchUsers = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      setUsers(await superAdminTenantService.getTenantUsers(tenantId));
    } catch (err: unknown) {
      const apiErr = err as { response?: { data?: { message?: string } } };
      setError(apiErr?.response?.data?.message || 'Failed to load users.');
    } finally {
      setLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  if (loading) {
    return (
      <div className="flex items-center gap-2 text-muted-foreground py-10 justify-center">
        <Loader2 className="animate-spin" size={18} /> Loading users…
      </div>
    );
  }

  if (error) {
    return <p className="text-rose-600 text-sm py-6">{error}</p>;
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        These are the tenant&apos;s user accounts. Use a user&apos;s email to help them recover a forgotten
        login, or reset a password if they&apos;re locked out — they&apos;ll be forced to set a new one on
        their next sign-in.
      </p>

      <div className="border border-border rounded-xl divide-y divide-border overflow-hidden">
        {users.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-10">No users found.</p>
        ) : (
          users.map((u) => (
            <div key={u.id} className="flex items-center gap-4 px-4 py-3 hover:bg-muted/40 transition-colors">
              <div className="w-9 h-9 rounded-full bg-blue-500/10 text-blue-600 flex items-center justify-center text-sm font-bold shrink-0">
                {u.firstName?.[0]}{u.lastName?.[0]}
              </div>
              <div className="flex-1 min-w-0">
                <div className="font-medium text-muted-foreground truncate">
                  {u.firstName} {u.lastName}
                </div>
                <div className="text-xs text-muted-foreground flex flex-wrap items-center gap-x-3 gap-y-0.5 mt-0.5">
                  <span className="flex items-center gap-1"><Mail size={11} /> {u.email}</span>
                  {u.phone && <span className="flex items-center gap-1"><Phone size={11} /> {u.phone}</span>}
                </div>
              </div>
              <div className="hidden sm:flex flex-wrap gap-1 max-w-[200px] justify-end">
                {u.roles.map((r) => (
                  <span key={r} className="text-[10px] font-semibold px-2 py-0.5 rounded-full border border-border bg-muted text-muted-foreground">
                    {r}
                  </span>
                ))}
              </div>
              <div className="text-xs text-right shrink-0 w-28 hidden md:block">
                {u.active ? (
                  <span className="inline-flex items-center gap-1 text-emerald-600"><CheckCircle2 size={12} /> Active</span>
                ) : (
                  <span className="inline-flex items-center gap-1 text-rose-600"><XCircle size={12} /> Inactive</span>
                )}
                <div className="text-muted-foreground mt-0.5">
                  {u.lastLoginAt ? format(new Date(u.lastLoginAt), 'MMM d, HH:mm') : 'Never logged in'}
                </div>
              </div>
              <button
                onClick={() => setResetting(u)}
                className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-border text-amber-600 hover:bg-amber-500/10 hover:border-amber-500/40 transition-colors flex items-center gap-1.5 shrink-0"
              >
                <KeyRound size={12} /> Reset
              </button>
            </div>
          ))
        )}
      </div>

      {resetting && (
        <ResetModal
          tenantId={tenantId}
          user={resetting}
          onClose={() => setResetting(null)}
          onDone={() => {
            setResetting(null);
            fetchUsers();
          }}
        />
      )}
    </div>
  );
}

function ResetModal({
  tenantId,
  user,
  onClose,
  onDone,
}: {
  tenantId: string;
  user: TenantUserResponse;
  onClose: () => void;
  onDone: () => void;
}) {
  const [newPassword, setNewPassword] = useState('');
  const [show, setShow] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (newPassword.trim().length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await superAdminTenantService.resetTenantUserPassword(tenantId, user.id, newPassword);
      onDone();
    } catch (err: unknown) {
      const apiErr = err as { response?: { data?: { message?: string } } };
      setError(apiErr?.response?.data?.message || 'Failed to reset password.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-background border border-border rounded-2xl p-7 w-full max-w-md shadow-2xl">
        <div className="flex items-center gap-3 mb-5">
          <div className="p-2.5 bg-amber-500/10 rounded-xl border border-amber-500/20">
            <ShieldAlert size={20} className="text-amber-500" />
          </div>
          <div>
            <h2 className="text-lg font-bold text-muted-foreground">Reset Password</h2>
            <p className="text-sm text-muted-foreground mt-0.5">{user.email}</p>
          </div>
        </div>

        <label className="text-xs text-muted-foreground mb-1.5 block">Temporary Password</label>
        <div className="relative">
          <input
            type={show ? 'text' : 'password'}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="Min 8 characters"
            className="w-full bg-card border border-border rounded-lg px-3 py-2 pr-10 text-sm text-muted-foreground focus:outline-none focus:ring-2 focus:ring-blue-500/40"
          />
          <button
            type="button"
            onClick={() => setShow((v) => !v)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
          >
            {show ? <EyeOff size={14} /> : <Eye size={14} />}
          </button>
        </div>

        <p className="text-xs text-muted-foreground bg-muted/50 border border-border rounded-lg px-3 py-2 mt-3">
          The user must change this password on their next login, and any active sessions will be signed out.
        </p>

        {error && <p className="text-sm text-rose-600 mt-3">{error}</p>}

        <div className="flex gap-3 mt-6">
          <button
            onClick={onClose}
            disabled={saving}
            className="flex-1 px-4 py-2 rounded-lg border border-border text-muted-foreground font-medium hover:bg-muted transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={submit}
            disabled={saving}
            className="flex-1 px-4 py-2 rounded-lg bg-amber-600 text-white font-medium hover:bg-amber-700 transition-colors flex items-center justify-center gap-2"
          >
            {saving && <Loader2 size={15} className="animate-spin" />}
            Reset Password
          </button>
        </div>
      </div>
    </div>
  );
}
