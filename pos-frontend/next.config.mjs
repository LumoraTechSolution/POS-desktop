/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  // When BACKEND_URL is set (production on Vercel/AWS), proxy the API through
  // this same origin so frontend and backend are same-origin: the backend's
  // auth cookies (sa-auth-token / auth-token) come back first-party and the
  // Next.js middleware can read them. Scoped to /api/v1/* so it never clobbers
  // the internal Next route handlers (/api/logout, /api/super-admin-logout).
  // Left unset in local dev, where the browser hits NEXT_PUBLIC_API_URL directly.
  async rewrites() {
    const target = process.env.BACKEND_URL;
    if (!target) return [];
    return [{ source: '/api/v1/:path*', destination: `${target}/api/v1/:path*` }];
  },
  // Security headers (CSP with per-request nonce, X-Frame-Options, etc.) are
  // applied in middleware.ts so they can carry a fresh nonce on every request.
  images: {
    // Product images are admin-supplied URLs, so the optimizer needs a host
    // allowlist rather than a fixed set. Set IMAGE_REMOTE_HOSTS to a comma-
    // separated host list (wildcards allowed, e.g. "*.cdn.example.com") to
    // shrink the optimizer's SSRF/DoS surface in production. Defaults to the
    // previous open allowlist so existing product images keep loading.
    remotePatterns: process.env.IMAGE_REMOTE_HOSTS
      ? process.env.IMAGE_REMOTE_HOSTS.split(',')
          .map((h) => h.trim())
          .filter(Boolean)
          .map((hostname) => ({ protocol: 'https', hostname }))
      : [
          { protocol: 'https', hostname: '**' },
          { protocol: 'http', hostname: 'localhost' },
        ],
  },
  experimental: {
    // Sentry's server SDK pulls in OpenTelemetry + require-in-the-middle, which
    // use dynamic `require` that webpack can't statically analyze. Externalising
    // them silences the "Critical dependency" warnings without breaking runtime.
    serverComponentsExternalPackages: [
      '@sentry/nextjs',
      '@opentelemetry/instrumentation',
      'require-in-the-middle',
    ],
  },
};

// Sentry is opt-in. The package may not be installed locally yet; if so we
// fall back to the bare config so `next build` still works in environments
// that don't have it.
async function buildExportedConfig() {
  if (!process.env.NEXT_PUBLIC_SENTRY_DSN) return nextConfig;
  try {
    const { withSentryConfig } = await import('@sentry/nextjs');
    return withSentryConfig(nextConfig, {
      silent: true,
      org: process.env.SENTRY_ORG,
      project: process.env.SENTRY_PROJECT,
      widenClientFileUpload: true,
      hideSourceMaps: true,
      disableLogger: true,
    });
  } catch {
    return nextConfig;
  }
}

export default await buildExportedConfig();
