import type { MetadataRoute } from 'next';

// Lumora POS is a private, login-gated internal application. Tell crawlers not
// to index any of it in case an instance is ever reachable from the internet.
export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: '*', disallow: '/' },
  };
}
