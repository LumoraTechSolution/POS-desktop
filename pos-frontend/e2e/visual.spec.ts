import { test, expect, type Page } from '@playwright/test';
import { loginAndOpenTerminal } from './helpers/login';

/**
 * Visual regression snapshots for the dashboard + POS terminal at the three
 * canonical viewports. Stores PNGs under e2e/visual.spec.ts-snapshots/ — these
 * are checked into git. Re-baseline with:
 *
 *   npm run test:e2e -- visual.spec.ts --update-snapshots
 *
 * Threshold is intentionally loose (maxDiffPixelRatio 0.02) — sub-pixel font
 * rendering varies by OS and we don't want flakes from that. Catastrophic
 * regressions (entire surfaces re-coloured, layout broken) will still trip it.
 */

const VIEWPORTS = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'laptop', width: 1280, height: 800 },
  { name: 'tablet', width: 1024, height: 768 },
] as const;

async function setTheme(page: Page, theme: 'dark' | 'light') {
  // next-themes persists in localStorage under "theme". Seed it before navigation
  // so the first paint is in the desired theme — avoids a flash mid-screenshot.
  await page.addInitScript((t) => {
    try {
      window.localStorage.setItem('theme', t);
    } catch {
      // sandbox may block storage; the test will use whichever theme is default.
    }
  }, theme);
}

async function freezeMotion(page: Page) {
  // Disable animations so screenshots aren't flaky.
  await page.addStyleTag({
    content: `
      *, *::before, *::after {
        animation-duration: 0s !important;
        animation-delay: 0s !important;
        transition-duration: 0s !important;
        transition-delay: 0s !important;
      }
    `,
  });
}

for (const vp of VIEWPORTS) {
  test.describe(`visual — ${vp.name} (${vp.width}x${vp.height})`, () => {
    test.use({ viewport: { width: vp.width, height: vp.height } });

    for (const theme of ['dark', 'light'] as const) {
      test(`login (${theme})`, async ({ page }) => {
        await setTheme(page, theme);
        await page.goto('/login');
        await freezeMotion(page);
        await expect(page.getByRole('heading').first()).toBeVisible();
        await expect(page).toHaveScreenshot(`login-${theme}-${vp.name}.png`, {
          maxDiffPixelRatio: 0.02,
          fullPage: true,
        });
      });
    }

    test('POS terminal (dark, forced)', async ({ page }) => {
      // Terminal pins itself to dark via className="dark" — theme prop is irrelevant.
      await loginAndOpenTerminal(page);

      const startModal = page.getByRole('dialog', { name: /start your shift/i });
      if (await startModal.isVisible().catch(() => false)) {
        await startModal.getByLabel(/opening cash/i).fill('100');
        await startModal.getByRole('button', { name: /start shift/i }).click();
        await startModal.waitFor({ state: 'hidden' });
      }

      await freezeMotion(page);
      await expect(page).toHaveScreenshot(`terminal-${vp.name}.png`, {
        maxDiffPixelRatio: 0.02,
        fullPage: true,
      });
    });
  });
}
