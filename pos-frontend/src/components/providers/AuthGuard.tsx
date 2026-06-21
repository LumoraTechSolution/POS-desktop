'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import axios from 'axios';
import { useAuthStore } from '@/stores/authStore';
import { authService } from '@/services/authService';
import { performLogout } from '@/lib/performLogout';

// Module-level: persists across component mounts within the same browser tab session.
// Cleared whenever the user logs out via the store subscription below.
let verifiedAt: number | null = null;
const SESSION_TTL_MS = 5 * 60 * 1000; // 5 minutes

// Backoff schedule for transient "backend not ready yet" failures during
// startup. Each entry is the delay (ms) before the next getMe() attempt, so the
// first try plus these = 5 total attempts over ~7s before we give up and show
// the Retry screen.
const RETRY_BACKOFFS_MS = [1000, 1500, 2000, 2500];

function isSessionRecentlyVerified(): boolean {
  return verifiedAt !== null && Date.now() - verifiedAt < SESSION_TTL_MS;
}

function isTokenExpired(token: string): boolean {
  try {
    // JWTs use base64url (- and _); atob() needs standard base64 (+ and /)
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64));
    return payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export default function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [isVerifying, setIsVerifying] = useState(true);
  const [unreachable, setUnreachable] = useState(false);
  // Bumping this re-runs the verification effect (used by the Retry button).
  const [retryNonce, setRetryNonce] = useState(0);

  const handleRetry = useCallback(() => {
    setUnreachable(false);
    setIsVerifying(true);
    setRetryNonce((n) => n + 1);
  }, []);

  useEffect(() => {
    let isMounted = true;

    // Clear the module-level verified timestamp whenever the store logs out.
    const unsubStore = useAuthStore.subscribe((state, prev) => {
      if (prev.isAuthenticated && !state.isAuthenticated) {
        verifiedAt = null;
      }
    });

    const checkAuth = async () => {
      const { isAuthenticated, token, passwordChangeRequired } = useAuthStore.getState();

      // A user mid-forced-password-change isn't fully authenticated yet — send
      // them to the dedicated change-password screen rather than into the app.
      if (passwordChangeRequired) {
        if (isMounted) router.replace('/change-password');
        return;
      }

      // token is memory-only since P1 1.6 — it is always null after a page reload.
      // Only bail out here if we have no session at all; the getMe() call below
      // will trigger the 401 → silent-refresh flow when token is null but
      // refreshToken is still in sessionStorage.
      if (!isAuthenticated) {
        await performLogout();
        if (isMounted) router.replace('/login');
        return;
      }

      // Already verified recently in this tab — skip the backend round-trip.
      // This avoids burning through the login rate-limit bucket during normal
      // navigation (getMe was previously called on every route-group change).
      if (isSessionRecentlyVerified()) {
        if (isMounted) setIsVerifying(false);
        return;
      }

      // Token is locally valid — show the UI immediately and verify in background.
      // In that case a transient verify failure must NOT yank the user out of a
      // working app, so we remember it and skip the Retry screen below.
      let shownEarly = false;
      if (token && !isTokenExpired(token) && isMounted) {
        setIsVerifying(false);
        shownEarly = true;
      }

      // Verify against the backend, retrying transient / backend-not-ready
      // failures. The refresh interceptor now has a timeout, so getMe() always
      // settles — this loop is bounded and the spinner can never stick.
      for (let attempt = 0; ; attempt++) {
        try {
          await authService.getMe();
          verifiedAt = Date.now();
          if (isMounted) setIsVerifying(false);
          return;
        } catch (err) {
          if (!isMounted) return;

          const status = axios.isAxiosError(err) ? err.response?.status : undefined;

          // 401/403 → the session is genuinely invalid. The interceptor's
          // refresh path already logs out on a failed refresh; performLogout is
          // idempotent, so call it defensively to cover any path that didn't.
          if (status === 401 || status === 403) {
            await performLogout();
            if (isMounted) router.replace('/login');
            return;
          }

          // Network error / timeout / 5xx → backend likely still booting.
          // Retry with backoff before giving up.
          if (attempt < RETRY_BACKOFFS_MS.length) {
            await delay(RETRY_BACKOFFS_MS[attempt]);
            if (!isMounted) return;
            continue;
          }

          // Retries exhausted. If we already let the user into the app on a
          // locally-valid token, leave them there; otherwise surface the Retry
          // screen instead of an endless spinner.
          if (isMounted) {
            setIsVerifying(false);
            if (!shownEarly) setUnreachable(true);
          }
          return;
        }
      }
    };

    // Guard against double-invocation (StrictMode, rapid re-mounts).
    let called = false;
    const runOnce = () => {
      if (called) return;
      called = true;
      checkAuth();
    };

    const unsub = useAuthStore.persist.onFinishHydration(() => {
      unsub();
      if (isMounted) runOnce();
    });

    // If already hydrated, don't wait for the event.
    if (useAuthStore.persist.hasHydrated()) {
      unsub();
      runOnce();
    }

    return () => {
      isMounted = false;
      unsubStore();
    };
  }, [router, retryNonce]);

  if (unreachable) {
    return (
      <div className="h-screen w-full flex flex-col items-center justify-center bg-black gap-4 px-6 text-center">
        <p className="text-foreground text-base font-semibold">Couldn&apos;t reach the server</p>
        <p className="text-muted-foreground text-sm max-w-sm">
          The app couldn&apos;t connect to the local service. It may still be starting up.
        </p>
        <button
          onClick={handleRetry}
          className="mt-2 rounded-md bg-primary px-5 py-2 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90"
        >
          Retry
        </button>
      </div>
    );
  }

  if (isVerifying) {
    return (
      <div className="h-screen w-full flex flex-col items-center justify-center bg-black gap-4">
        <div className="h-12 w-12 rounded-full border-t-2 border-b-2 border-primary animate-spin"></div>
        <p className="text-muted-foreground text-sm font-medium animate-pulse">Verifying session...</p>
      </div>
    );
  }

  return <>{children}</>;
}
