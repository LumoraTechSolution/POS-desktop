import { test, expect, type Page, type Locator } from "@playwright/test";
import { loginAsCashier } from "./helpers/login";
import { resetCashierShift, parseMoney } from "./helpers/cash-session-setup";

/**
 * F7 — post-completion payment correction.
 *
 * F7 opens a "Correct a sale" picker listing the current shift's sales; choosing
 * one opens the correction modal for that sale. Two flows are covered:
 *
 *  1. CASH → CARD: the cash leaves the drawer, so end-of-shift reconciles back
 *     to the opening float (cash sales drop to 0).
 *  2. CARD → CASH with an explicit amount: switching to cash must ask how much
 *     the customer actually handed over (not assume an exact tender) and then
 *     count toward the drawer.
 */
const OPEN_BAL = 200;

async function openShift(page: Page) {
  const startModal = page.getByRole("dialog", { name: /start your shift/i });
  await expect(startModal).toBeVisible();
  await startModal.getByLabel(/opening cash/i).fill(String(OPEN_BAL));
  await startModal.getByRole("button", { name: /start shift/i }).click();
  await expect(startModal).toBeHidden();
}

/** Rings up the first available product on the given method. Returns its price. */
async function ringSale(page: Page, method: "CASH" | "CARD"): Promise<number> {
  const productCard = page.locator('[data-product-card][data-disabled="false"]').first();
  await expect(productCard).toBeVisible();
  const price = parseMoney((await productCard.getAttribute("data-price")) ?? "0");
  expect(price).toBeGreaterThan(0);
  await productCard.click();

  await page.getByRole("button", { name: /charge/i }).click();
  await page.getByRole("radio", { name: new RegExp(method, "i") }).click();
  if (method === "CASH") {
    await page.getByRole("button", { name: /^exact$/i }).click();
  }
  await page.getByRole("button", { name: /complete sale/i }).click();
  await expect(page.getByText(/sale processed/i)).toBeVisible();
  return price;
}

/** Opens F7 picker and selects the most recent sale, returning the correction modal. */
async function pickLatestSale(page: Page): Promise<Locator> {
  await page.keyboard.press("F7");
  const picker = page.getByRole("dialog", { name: /correct a sale/i });
  await expect(picker).toBeVisible();
  await picker.locator("button", { hasText: "INV-" }).first().click();

  const correctDialog = page.getByRole("dialog", { name: /correct payment/i });
  await expect(correctDialog).toBeVisible();
  return correctDialog;
}

/** Clicks digits on the in-modal NumberPad to enter an amount string. */
async function typeOnPad(scope: Locator, amount: string) {
  const pad = scope.getByRole("group", { name: /numeric keypad/i });
  for (const ch of amount) {
    const name = ch === "." ? "Decimal point" : ch;
    await pad.getByRole("button", { name, exact: true }).click();
  }
}

test.describe("F7 — correct a sale", () => {
  test.beforeEach(async ({ request }) => {
    await resetCashierShift(request);
  });

  test("cashier flips CASH → CARD via the picker; drawer reconciles to opening float", async ({ page }) => {
    await loginAsCashier(page);
    await page.waitForURL(/\/terminal/);
    await openShift(page);

    await ringSale(page, "CASH");

    const correctDialog = await pickLatestSale(page);
    await correctDialog.getByRole("button", { name: /change payment method/i }).click();
    await correctDialog.getByRole("radio", { name: /card/i }).click();
    await correctDialog.getByRole("button", { name: /^apply$/i }).click();

    await expect(page.getByText(/payment corrected/i)).toBeVisible();
    await expect(correctDialog).toBeHidden();

    // CASH → CARD zeroes the sale's cash, so the drawer should reconcile to float.
    await page.getByRole("button", { name: /end shift/i }).click();
    const endModal = page.getByRole("dialog", { name: /end shift/i });
    await expect(endModal).toBeVisible();

    await expect
      .poll(async () => parseMoney(await endModal.getByTestId("end-shift-cash-sales").innerText()))
      .toBe(0);

    const expectedDrawer = parseMoney(await endModal.getByTestId("end-shift-expected").innerText());
    expect(Math.abs(expectedDrawer - OPEN_BAL)).toBeLessThan(0.01);

    await endModal.getByLabel(/counted cash/i).fill(String(expectedDrawer));
    await expect(endModal.getByText(/balances exactly/i)).toBeVisible();

    await endModal.getByRole("button", { name: /end shift/i }).click();
    await expect(page.getByText(/drawer balanced/i)).toBeVisible();
  });

  test("cashier flips CARD → CASH via the picker; must enter the amount paid", async ({ page }) => {
    await loginAsCashier(page);
    await page.waitForURL(/\/terminal/);
    await openShift(page);

    const price = await ringSale(page, "CARD");

    const correctDialog = await pickLatestSale(page);
    await correctDialog.getByRole("button", { name: /change payment method/i }).click();
    await correctDialog.getByRole("radio", { name: /cash/i }).click();
    // Switching to cash routes to amount entry rather than applying immediately.
    await correctDialog.getByRole("button", { name: /^next$/i }).click();

    // The amount-entry step appears with the total due and a live change line.
    await expect(correctDialog.getByText(/total due/i)).toBeVisible();
    await expect(correctDialog.getByText(/change to return/i)).toBeVisible();

    // Enter a gross that comfortably exceeds the net (covers any tax on top).
    const tender = String(Math.round(price) + 1000);
    await typeOnPad(correctDialog, tender);
    await correctDialog.getByRole("button", { name: /^apply$/i }).click();

    await expect(page.getByText(/payment corrected/i)).toBeVisible();
    await expect(correctDialog).toBeHidden();

    // The corrected sale is now cash, so it counts toward the drawer (> float).
    await page.getByRole("button", { name: /end shift/i }).click();
    const endModal = page.getByRole("dialog", { name: /end shift/i });
    await expect(endModal).toBeVisible();

    await expect
      .poll(async () => parseMoney(await endModal.getByTestId("end-shift-cash-sales").innerText()))
      .toBeGreaterThan(0);

    const expectedDrawer = parseMoney(await endModal.getByTestId("end-shift-expected").innerText());
    expect(expectedDrawer).toBeGreaterThan(OPEN_BAL);
  });
});
