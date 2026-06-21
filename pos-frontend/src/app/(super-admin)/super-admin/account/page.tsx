'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { format } from 'date-fns';
import {
  UserCircle,
  Mail,
  Clock,
  Globe,
  Monitor,
  ShieldCheck,
  KeyRound,
  Loader2,
  AlertTriangle,
} from 'lucide-react';
import { toast } from 'sonner';

import { superAdminAuthService } from '@/services/superAdminAuthService';
import { useSuperAdminStore } from '@/stores/superAdminStore';
import { QK } from '@/lib/queryKeys';
import { getApiErrorMessage } from '@/lib/utils';

export default function SuperAdminAccountPage() {
  const router = useRouter();
  const logout = useSuperAdminStore((s) => s.logout);

  const profileQuery = useQuery({
    queryKey: QK.superAdmin.me,
    queryFn: superAdminAuthService.getProfile,
  });

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center gap-4 bg-background p-6 rounded-xl border border-border shadow-sm">
        <div className="w-14 h-14 bg-blue-50 text-blue-600 border border-blue-100 rounded-full flex items-center justify-center shrink-0">
          <UserCircle className="w-7 h-7" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-muted-foreground">My account</h1>
          <p className="text-muted-foreground mt-1 text-sm">
            Review your most recent session and rotate your password.
          </p>
        </div>
      </div>

      <ProfileCard
        loading={profileQuery.isLoading}
        error={profileQuery.error ? getApiErrorMessage(profileQuery.error, 'Could not load profile') : null}
        profile={profileQuery.data}
      />

      <ChangePasswordCard
        onChanged={async () => {
          // Backend revokes every refresh token on success — force a clean re-login.
          logout();
          try {
            await fetch('/api/super-admin-logout', { method: 'POST' });
          } catch {
            /* swallow */
          }
          router.push('/super-admin/login?changed=1');
        }}
      />
    </div>
  );
}

interface ProfileCardProps {
  loading: boolean;
  error: string | null;
  profile: Awaited<ReturnType<typeof superAdminAuthService.getProfile>> | undefined;
}

function ProfileCard({ loading, error, profile }: ProfileCardProps) {
  return (
    <section className="bg-background rounded-2xl border border-border shadow-sm overflow-hidden">
      <header className="px-6 py-4 border-b border-border flex items-center gap-2">
        <ShieldCheck className="w-5 h-5 text-blue-500" />
        <h2 className="font-semibold text-muted-foreground">Profile & last session</h2>
      </header>

      <div className="p-6">
        {loading && (
          <div className="flex items-center gap-3 text-muted-foreground text-sm">
            <Loader2 className="w-4 h-4 animate-spin" /> Loading profile…
          </div>
        )}

        {error && !loading && (
          <div className="flex items-start gap-3 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {profile && !loading && (
          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-5 text-sm">
            <ProfileRow icon={<UserCircle className="w-4 h-4" />} label="Name" value={profile.fullName} />
            <ProfileRow icon={<Mail className="w-4 h-4" />} label="Email" value={profile.email} />
            <ProfileRow
              icon={<Clock className="w-4 h-4" />}
              label="Last login"
              value={profile.lastLoginAt ? format(new Date(profile.lastLoginAt), 'PP p') : 'Never'}
            />
            <ProfileRow
              icon={<Globe className="w-4 h-4" />}
              label="Last login IP"
              value={profile.lastLoginIp ?? 'Unknown'}
              mono
            />
            <div className="sm:col-span-2">
              <ProfileRow
                icon={<Monitor className="w-4 h-4" />}
                label="Last user agent"
                value={profile.lastLoginUserAgent ?? 'Unknown'}
                mono
              />
            </div>
            <ProfileRow
              icon={<Clock className="w-4 h-4" />}
              label="Account created"
              value={profile.createdAt ? format(new Date(profile.createdAt), 'PP') : '—'}
            />
          </dl>
        )}
      </div>
    </section>
  );
}

function ProfileRow({
  icon,
  label,
  value,
  mono,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div>
      <dt className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-muted-foreground mb-1">
        {icon}
        {label}
      </dt>
      <dd className={`text-muted-foreground ${mono ? 'font-mono text-xs break-all' : ''}`}>{value}</dd>
    </div>
  );
}

function ChangePasswordCard({ onChanged }: { onChanged: () => Promise<void> }) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

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

    try {
      setSubmitting(true);
      await superAdminAuthService.changePassword({ currentPassword, newPassword });
      toast.success('Password updated. Signing you out…');
      await onChanged();
    } catch (err) {
      setError(getApiErrorMessage(err, 'Could not change password. Please try again.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="bg-background rounded-2xl border border-border shadow-sm overflow-hidden">
      <header className="px-6 py-4 border-b border-border flex items-center gap-2">
        <KeyRound className="w-5 h-5 text-blue-500" />
        <h2 className="font-semibold text-muted-foreground">Change password</h2>
      </header>

      <form onSubmit={handleSubmit} className="p-6 space-y-5">
        <p className="text-sm text-muted-foreground">
          Changing your password will revoke every other active session and sign you out of this one.
        </p>

        {error && (
          <div className="flex items-start gap-3 rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">
            <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        <PasswordField
          id="currentPassword"
          label="Current password"
          value={currentPassword}
          onChange={setCurrentPassword}
          autoComplete="current-password"
        />
        <PasswordField
          id="newPassword"
          label="New password"
          hint="Minimum 12 characters."
          value={newPassword}
          onChange={setNewPassword}
          autoComplete="new-password"
        />
        <PasswordField
          id="confirmPassword"
          label="Confirm new password"
          value={confirmPassword}
          onChange={setConfirmPassword}
          autoComplete="new-password"
        />

        <div className="flex justify-end pt-2">
          <button
            type="submit"
            disabled={submitting}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-blue-700 disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
            {submitting ? 'Updating…' : 'Update password'}
          </button>
        </div>
      </form>
    </section>
  );
}

function PasswordField({
  id,
  label,
  hint,
  value,
  onChange,
  autoComplete,
}: {
  id: string;
  label: string;
  hint?: string;
  value: string;
  onChange: (v: string) => void;
  autoComplete?: string;
}) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-muted-foreground mb-1">
        {label}
      </label>
      <input
        id={id}
        type="password"
        required
        autoComplete={autoComplete}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-lg border border-border px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      {hint && <p className="text-xs text-muted-foreground mt-1">{hint}</p>}
    </div>
  );
}
