'use client';

import { Button } from '@/components/ui/button';
import { formatCurrency } from '@/lib/format';
import { cn } from '@/lib/utils';

type QuickTenderButtonsProps = {
  /** Amount due — used for the "Exact" preset. */
  total: number;
  /** Custom presets. Defaults to [100, 500, 1000, 2000, 5000]. */
  presets?: number[];
  /** Called with the selected tender amount. */
  onTender: (amount: number) => void;
  currency?: string;
  locale?: string;
  className?: string;
};

const DEFAULT_PRESETS = [100, 500, 1000, 2000, 5000];

/**
 * Preset cash buttons + an "Exact" affordance. Generates round-up suggestions
 * by skipping presets ≤ the amount due.
 */
export function QuickTenderButtons({
  total,
  presets = DEFAULT_PRESETS,
  onTender,
  currency,
  locale,
  className,
}: QuickTenderButtonsProps) {
  const candidates = presets.filter((p) => p >= total);
  const shown = candidates.length > 0 ? candidates.slice(0, 5) : presets.slice(-5);

  return (
    <div
      className={cn('grid grid-cols-2 gap-2', className)}
      role="group"
      aria-label="Quick tender presets"
    >
      <Button
        type="button"
        variant="default"
        size="touch"
        onClick={() => onTender(total)}
        disabled={total <= 0}
      >
        Exact
      </Button>
      {shown.map((amount) => (
        <Button
          key={amount}
          type="button"
          variant="outline"
          size="touch"
          onClick={() => onTender(amount)}
          className="font-semibold"
        >
          {formatCurrency(amount, currency, locale)}
        </Button>
      ))}
    </div>
  );
}
