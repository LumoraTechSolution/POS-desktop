import type { APIRequestContext } from "@playwright/test";
import { API_URL, TEST_USER } from "../fixtures/test-credentials";

const V1 = `${API_URL}/api/v1`;

async function loginToken(request: APIRequestContext): Promise<string> {
  // One business per deployment: login takes only email + password; the backend
  // resolves the tenant from the user.
  const res = await request.post(`${V1}/auth/login`, {
    data: { email: TEST_USER.email, password: TEST_USER.password },
  });
  const body = await res.json();
  if (!res.ok() || !body?.success) {
    throw new Error(`Cashier login failed: ${body?.message ?? res.status()}`);
  }
  return body.data.accessToken;
}

/**
 * Puts the seeded cashier into a clean pre-test state via the API: if a cash
 * session is still OPEN (e.g. a previous run failed mid-flow), close it so the
 * terminal shows the Start Shift gate again. Closing the session also clocks the
 * cashier out (CashSessionService.endShift). Idempotent — safe to call when no
 * session is open.
 */
export async function resetCashierShift(request: APIRequestContext): Promise<void> {
  const token = await loginToken(request);
  const headers = { Authorization: `Bearer ${token}` };

  const activeRes = await request.get(`${V1}/cash-session/active`, { headers });
  const activeBody = await activeRes.json().catch(() => ({}));
  const session = activeBody?.data;

  if (session && session.status === "OPEN") {
    // Close at the expected balance so we leave a $0-variance, tidy record.
    const closingBalance =
      session.expectedBalance ??
      (session.openingBalance ?? 0) + (session.cashSalesTotal ?? 0) - (session.cashRefundsTotal ?? 0);
    await request.post(`${V1}/cash-session/end`, {
      headers,
      data: { closingBalance, notes: "e2e cleanup" },
    });
  }
}

/** Strip currency symbol / thousands separators, leaving a parseable number. */
export function parseMoney(text: string): number {
  return Number(text.replace(/[^0-9.-]/g, ""));
}
