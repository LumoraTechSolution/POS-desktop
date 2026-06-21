'use client';

import React, { useEffect, useState } from 'react';
import { useRouter, usePathname } from 'next/navigation';
import { useSuperAdminStore } from '@/stores/superAdminStore';
import { ShieldAlert } from 'lucide-react';
import Link from 'next/link';
import { ThemeToggle } from '@/components/ui/theme-toggle';
import { cn } from '@/lib/utils';

export default function SuperAdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const { logout } = useSuperAdminStore();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    const checkAuth = () => {
      const isLoginPage = pathname === '/super-admin/login';
      const isChangePasswordPage = pathname === '/super-admin/change-password';
      const state = useSuperAdminStore.getState();
      const authenticated = state.isAuthenticated;
      const mustChangePassword = state.user?.passwordChangeRequired === true;

      if (!authenticated && !isLoginPage) {
        router.push('/super-admin/login');
      } else if (authenticated && mustChangePassword && !isChangePasswordPage) {
        router.push('/super-admin/change-password');
      } else if (authenticated && !mustChangePassword && isLoginPage) {
        router.push('/super-admin');
      }
      setMounted(true);
    };

    if (useSuperAdminStore.persist.hasHydrated()) {
      checkAuth();
    } else {
      useSuperAdminStore.persist.onFinishHydration(() => {
        checkAuth();
      });
    }
  }, [pathname, router]);

  if (!mounted) return null;

  const isLoginPage = pathname === '/super-admin/login';
  const isChangePasswordPage = pathname === '/super-admin/change-password';

  if (isLoginPage || isChangePasswordPage) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        {children}
      </div>
    );
  }

  const navItems = [
    { href: '/super-admin', label: 'Dashboard', exact: true },
    { href: '/super-admin/tenants', label: 'Tenants' },
    { href: '/super-admin/audit-log', label: 'Audit Logs' },
    { href: '/super-admin/account', label: 'My Account' },
  ];

  return (
    <div className="min-h-screen bg-background text-foreground flex">
      <aside className="w-64 bg-card border-r border-border flex flex-col">
        <div className="p-6 border-b border-border flex items-center gap-3">
          <ShieldAlert className="w-7 h-7 text-primary" />
          <h1 className="text-lg font-bold tracking-tight text-foreground">Lumora HQ</h1>
        </div>

        <nav className="flex-1 p-3 space-y-1" aria-label="Super-admin navigation">
          {navItems.map((item) => {
            const isActive = item.exact ? pathname === item.href : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={isActive ? 'page' : undefined}
                className={cn(
                  'block px-4 py-2.5 rounded-lg font-medium text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  isActive
                    ? 'bg-primary/10 text-primary font-semibold'
                    : 'text-muted-foreground hover:text-foreground hover:bg-accent/10'
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="p-3 border-t border-border">
          <button
            onClick={async () => {
              const refreshToken = useSuperAdminStore.getState().refreshToken;
              await fetch('/api/super-admin-logout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(refreshToken ? { refreshToken } : {}),
              }).catch(() => {});
              logout();
              router.push('/super-admin/login');
            }}
            className="w-full text-left px-4 py-2.5 rounded-lg text-sm font-medium text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            Sign Out
          </button>
        </div>
      </aside>

      <main className="flex-1 flex flex-col min-h-screen overflow-hidden">
        <header className="relative h-16 border-b border-border shrink-0 bg-gradient-to-r from-violet-600/15 via-indigo-600/10 to-transparent">
          <div className="absolute inset-y-0 left-0 w-1 bg-gradient-to-b from-violet-500 to-indigo-500" aria-hidden="true" />
          <div className="h-full flex items-center justify-between px-8">
            <div className="flex items-center gap-2">
              <ShieldAlert size={18} className="text-violet-400" />
              <h2 className="text-base font-semibold text-foreground">Super Admin Portal</h2>
              <span className="ml-2 px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-widest bg-violet-500/15 text-violet-300 border border-violet-500/30">
                HQ
              </span>
            </div>
            <ThemeToggle />
          </div>
        </header>
        <div className="flex-1 overflow-auto p-8 bg-background">{children}</div>
      </main>
    </div>
  );
}
