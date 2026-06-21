# Lumora POS — E2E (Playwright)

Playwright tests live here. They drive a real browser against a running stack;
they do not stub the network.

## Running locally

```bash
# 1. From repo root — bring the stack up.
docker compose up -d database backend frontend

# 2. From frontend/ — install Playwright browsers (one-time).
npx playwright install --with-deps chromium

# 3. Run the suite.
npm run test:e2e

# Or with the UI runner (interactive, time-travel debugger).
npm run test:e2e:ui
```

## Suites

| Spec | What it covers | Needs |
|------|----------------|-------|
| `a11y.spec.ts` | axe-core (WCAG 2.1 AA) on login, super-admin login, the POS terminal, and the main dashboard pages. Fails on `serious`+ violations; `color-contrast` is logged-but-non-blocking on the dashboard (brand-palette decision — see `helpers/a11y.ts`). | Seeded admin |
| `cash-session.spec.ts` | The money path: open drawer → ring up a sale → close drawer with $0 variance. | Seeded cashier + ≥1 in-stock product |
| `visual.spec.ts` | Screenshot regression of login (dark+light) and the terminal, at 3 viewports. | Baselines for the current OS (see below) |

## Required test data

The tests log in with accounts that the backend's Flyway migrations seed into the
demo tenant on boot — no manual setup needed against a fresh DB:

- **Cashier** (`cash-session`): `cashier@demo.lumora.com` / `Cashier123!` — seeded by `V42__seed_cashier_user.sql`.
- **Terminal-capable account** (`a11y`/`visual`): `admin@demo.lumora.com` / `admin123` (ADMIN can open the terminal). Used by `loginAndOpenTerminal`.

Credentials live in `fixtures/test-credentials.ts` and are env-overridable
(`E2E_EMAIL`, `E2E_PASSWORD`, `E2E_DOMAIN`, and the `E2E_TERMINAL_*` variants):

```bash
E2E_EMAIL=test+cashier@example.com E2E_PASSWORD=…  npm run test:e2e
```

`cash-session` also needs **at least one active, in-stock product** on the
cashier's branch (migrations seed users, not products) — create one in the UI
locally, or via the API (CI does the latter).

## Cash-session flow

`cash-session.spec.ts` is the canonical test that protects the
"open drawer → sell → close drawer" loop. It's currency- and price-agnostic:

1. A `beforeEach` closes any leftover OPEN session via the API, so each run
   starts from a clean slate (no manual cleanup needed).
2. Log in as the cashier (lands on `/terminal`) and open a shift.
3. Ring up the first available product — reading its price from the card's
   `data-price` attribute rather than assuming a value.
4. Pay CASH, exact tender, complete the sale.
5. End the shift, count exactly the modal's **Expected** figure, and assert the
   "drawer balanced" toast + that the cash sale registered.

## Continuous integration

Two GitHub Actions workflows live in the umbrella repo (`lumora_pos/.github/workflows/`):

- **`ci.yml`** — fast checks on every push/PR: backend `mvnw clean verify` (+ a
  Flyway duplicate-version guard) and frontend typecheck / lint / `npm test` /
  build. **Does not run Playwright.**
- **`e2e.yml`** — this browser suite, on PRs to `main`/`development` and manual
  dispatch. It boots Postgres + backend + frontend in the runner, seeds a
  product via the API, and runs `a11y.spec.ts` + `cash-session.spec.ts`. It sets
  `RATE_LIMIT_LOGIN_CAPACITY=100000` so the per-IP login throttle doesn't block a
  suite that logs in repeatedly. A Playwright report is uploaded as an artifact.

`visual.spec.ts` is **not** run in CI yet — see below.

## Visual regression baselines

`visual.spec.ts` compares screenshots against committed baselines in
`e2e/visual.spec.ts-snapshots/`. Playwright suffixes them by OS, so baselines
are **not portable** — `*-win32.png` won't match a Linux CI runner (font
rendering differs).

Regenerate baselines for **your** OS after an intentional UI change:

```bash
npm run test:e2e -- visual.spec.ts --update-snapshots
```

To run `visual.spec.ts` in CI (Linux), generate matching baselines once with the
Playwright Docker image and commit the resulting `*-linux.png` files, then add
`visual.spec.ts` to the run step in `e2e.yml`:

```bash
docker run --rm -v "$PWD":/work -w /work/frontend \
  mcr.microsoft.com/playwright:v1.49.1-jammy \
  npx playwright test visual.spec.ts --update-snapshots
# (match the image tag to the @playwright/test version in package.json)
```
