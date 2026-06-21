'use client';

import { ThemeProvider as NextThemesProvider } from 'next-themes';
import type { ReactNode } from 'react';

export function ThemeProvider({ children, nonce }: { children: ReactNode; nonce?: string }) {
  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="dark"
      enableSystem
      disableTransitionOnChange
      // Stamp the per-request CSP nonce onto next-themes' injected inline script,
      // otherwise the strict script-src policy blocks it on every page load.
      nonce={nonce}
    >
      {children}
    </NextThemesProvider>
  );
}
