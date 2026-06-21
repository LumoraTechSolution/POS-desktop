// Sentry — browser runtime config.
// Initialized only when NEXT_PUBLIC_SENTRY_DSN is set, so dev environments
// without a DSN ship a no-op build. User context (userId/tenantId) is set
// from authStore via `src/lib/sentry.ts` once the user logs in.
import * as Sentry from "@sentry/nextjs";

const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN;

if (dsn) {
  Sentry.init({
    dsn,
    environment: process.env.NEXT_PUBLIC_SENTRY_ENV ?? process.env.NODE_ENV,
    release: process.env.NEXT_PUBLIC_SENTRY_RELEASE,
    tracesSampleRate: Number(process.env.NEXT_PUBLIC_SENTRY_TRACES_SAMPLE_RATE ?? 0.1),
    replaysSessionSampleRate: 0,
    replaysOnErrorSampleRate: 1.0,
    integrations: [
      Sentry.replayIntegration({ maskAllText: true, blockAllMedia: true }),
    ],
    beforeSend(event) {
      // Strip PII that may have leaked into breadcrumbs (cashier names, customer phone numbers).
      if (event.request?.cookies) delete event.request.cookies;
      return event;
    },
  });
}
