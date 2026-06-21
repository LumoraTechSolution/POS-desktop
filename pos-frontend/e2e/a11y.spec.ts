import { test, expect } from '@playwright/test';
import { checkA11y } from './helpers/a11y';
import { login, loginAndOpenTerminal } from './helpers/login';
import { TERMINAL_USER } from './fixtures/test-credentials';

/**
 * a11y smoke — runs axe-core (WCAG 2.1 A/AA) on the highest-traffic surfaces.
 * Failing threshold defaults to "serious"; minor/moderate violations are logged
 * but not blocking, so this can land without instantly red-lining the build.
 *
 * Setup: same as cash-session.spec.ts — backend + frontend reachable, seed
 * cashier exists. See e2e/README.md.
 *
 * Tuning: when a known violation is being worked on but not yet shipped, add
 * its rule id to disableRules below (with a comment + ticket reference). Don't
 * silently widen failOn — that defeats the purpose.
 */
test.describe('a11y smoke', () => {
  test('login page passes axe (WCAG 2.1 AA)', async ({ page }) => {
    await page.goto('/login');
    await checkA11y(page);
  });

  test('super-admin login page passes axe', async ({ page }) => {
    await page.goto('/super-admin/login');
    await checkA11y(page);
  });

  test('POS terminal (after start-shift) passes axe', async ({ page }) => {
    await loginAndOpenTerminal(page);

    // If the StartShiftModal is up, open the drawer first so the actual terminal
    // chrome (header, products, cart, hotkey legend, checkout) is what gets audited.
    const startModal = page.getByRole('dialog', { name: /start your shift/i });
    if (await startModal.isVisible().catch(() => false)) {
      await startModal.getByLabel(/opening cash/i).fill('100');
      await startModal.getByRole('button', { name: /start shift/i }).click();
      await startModal.waitFor({ state: 'hidden' });
    }

    await checkA11y(page, {
      // The barcode-scanner global keydown handler attaches to <body>; axe sometimes
      // flags it as an interactive element without role. False positive — skip.
      disableRules: [],
      // Same brand --primary mid-tone contrast debt as the dashboard audits below:
      // the primary "Start Shift" button / accents don't clear 4.5:1. Logged but not
      // blocking — it's a palette decision tracked separately, not a rebrand for CI.
      nonBlockingRules: ['color-contrast'],
    });
  });
});

/**
 * Dashboard pages — audited in the default (dark) theme as an admin. These carry
 * the bulk of the data tables, forms, and icon buttons, so they're where most
 * real violations live.
 */
test.describe('a11y — dashboard', () => {
  const ROUTES: { path: string; heading: RegExp }[] = [
    { path: '/overview', heading: /dashboard/i },
    { path: '/customers', heading: /customer/i },
    { path: '/inventory/products', heading: /product/i },
    { path: '/inventory/categories', heading: /categor/i },
    { path: '/inventory/suppliers', heading: /supplier/i },
    { path: '/employees', heading: /employee management/i },
    { path: '/settings', heading: /settings/i },
  ];

  test.beforeEach(async ({ page }) => {
    await login(page, TERMINAL_USER); // ADMIN -> /overview
    await page.waitForURL(/\/overview/);
  });

  for (const { path, heading } of ROUTES) {
    test(`${path} passes axe`, async ({ page }) => {
      await page.goto(path);
      await expect(page.getByRole('heading', { name: heading }).first()).toBeVisible();
      await checkA11y(page, {
        // color-contrast is still run + logged, but not blocking here: the remaining
        // failures stem from the brand --primary being a mid-tone blue where neither
        // black nor white text clears 4.5:1. Fixing that is a palette decision
        // tracked separately — don't silently rebrand just to make CI green.
        nonBlockingRules: ['color-contrast'],
      });
    });
  }
});
