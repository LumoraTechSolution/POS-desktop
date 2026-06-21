// Helpers for tying Sentry events to the authenticated user.
//
// `bindSentryUser(user)` is called from the auth flow on login; `clearSentryUser()`
// from logout. Both are safe to call when Sentry is not initialized (no DSN set).
import * as Sentry from "@sentry/nextjs";
import type { User } from "@/types/auth";

export function bindSentryUser(user: User | null): void {
  if (!process.env.NEXT_PUBLIC_SENTRY_DSN) return;
  if (!user) {
    Sentry.setUser(null);
    return;
  }
  Sentry.setUser({
    id: user.id,
    email: user.email,
    // tenantId goes in the user payload AND as a tag, so it can both filter
    // an event and show on the user card.
    tenantId: user.tenantId,
  });
  Sentry.setTag("tenantId", user.tenantId);
  Sentry.setTag("planTier", user.planTier);
}

export function clearSentryUser(): void {
  if (!process.env.NEXT_PUBLIC_SENTRY_DSN) return;
  Sentry.setUser(null);
  Sentry.setTag("tenantId", undefined as unknown as string);
}
