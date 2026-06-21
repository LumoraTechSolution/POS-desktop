import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import { headers } from 'next/headers';
import './globals.css';
import { QueryProvider } from '@/components/providers/QueryProvider';
import { ThemeProvider } from '@/components/providers/ThemeProvider';
import { Toaster } from 'sonner';

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'StoreX',
  description: 'Enterprise Point of Sale System powered by Lumora Tech',
  robots: { index: false, follow: false },
//   manifest: '/manifest.json',
};

// Reading headers() opts the entire tree into dynamic rendering, so the
// per-request nonce set by middleware.ts gets stamped onto Next.js's inline
// hydration scripts. Without this the page renders statically at build time
// with no nonce, and the runtime CSP blocks every script.
export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const nonce = headers().get('x-nonce') ?? undefined;
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={inter.className}>
        <ThemeProvider nonce={nonce}>
          <QueryProvider>
            {children}
          </QueryProvider>
          <Toaster position="top-right" richColors />
        </ThemeProvider>
      </body>
    </html>
  );
}
