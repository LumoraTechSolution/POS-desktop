'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/stores/authStore';

/**
 * Root page — redirects based on auth state.
 */
export default function Home() {
  const router = useRouter();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const loginMethod = useAuthStore((s) => s.loginMethod);

  useEffect(() => {
    if (isAuthenticated) {
      // Mirror the login routing on app reopen: a full email/password session
      // lands on the dashboard, while a PIN (at-the-register) session goes
      // straight to the POS terminal. Without this, every restored session was
      // dumped on /terminal — which forced the Start-Shift / cash-drawer prompt
      // on reopen even for a manager who just wanted the dashboard.
      router.replace(loginMethod === 'PIN' ? '/terminal' : '/overview');
    } else {
      router.replace('/login');
    }
  }, [isAuthenticated, loginMethod, router]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-primary"></div>
    </div>
  );
}
