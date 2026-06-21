import { expect, type Page } from '@playwright/test';
import { AxeBuilder } from '@axe-core/playwright';

type Severity = 'minor' | 'moderate' | 'serious' | 'critical';

type CheckOptions = {
  /** Violations at or above this severity fail the test. Default 'serious'. */
  failOn?: Severity;
  /** axe rules to skip entirely (not run, not logged). */
  disableRules?: string[];
  /** Rules that are still run + logged, but never fail the test. Use for known,
   *  tracked debt (e.g. a brand-palette contrast decision) so it stays visible. */
  nonBlockingRules?: string[];
  /** Only audit elements matching this selector. */
  include?: string;
  /** Exclude these selectors. Useful for third-party widgets. */
  exclude?: string[];
};

const RANK: Record<Severity, number> = { minor: 0, moderate: 1, serious: 2, critical: 3 };

/**
 * Runs axe-core against the current page and fails the test if any violation
 * meets or exceeds the failOn threshold. Logs all violations (above + below)
 * with their target selectors so triage doesn't require re-running.
 */
export async function checkA11y(page: Page, opts: CheckOptions = {}): Promise<void> {
  const failOn: Severity = opts.failOn ?? 'serious';

  let builder = new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']);
  if (opts.disableRules?.length) builder = builder.disableRules(opts.disableRules);
  if (opts.include) builder = builder.include(opts.include);
  if (opts.exclude?.length) for (const sel of opts.exclude) builder = builder.exclude(sel);

  const { violations } = await builder.analyze();

  if (violations.length > 0) {
    const formatted = violations
      .map((v) => {
        const targets = v.nodes
          .slice(0, 3)
          .map((n) => `      ${n.target.join(', ')}`)
          .join('\n');
        const more = v.nodes.length > 3 ? `\n      …and ${v.nodes.length - 3} more` : '';
        return `  [${v.impact ?? 'unknown'}] ${v.id} — ${v.help}\n${targets}${more}`;
      })
      .join('\n\n');
    console.warn(`\naxe-core violations:\n${formatted}\n`);
  }

  const nonBlocking = new Set(opts.nonBlockingRules ?? []);
  const blocking = violations.filter(
    (v) => v.impact && RANK[v.impact as Severity] >= RANK[failOn] && !nonBlocking.has(v.id)
  );
  expect(
    blocking.map((v) => v.id),
    `Expected no ${failOn}-or-higher a11y violations, found ${blocking.length}`
  ).toEqual([]);
}
