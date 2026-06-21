'use client';

import { Delete } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

type NumberPadProps = {
  value: string;
  onChange: (next: string) => void;
  /** Hide the decimal key (useful for PIN entry). */
  integerOnly?: boolean;
  /** Maximum digits including decimal point — defaults to 12. */
  maxLength?: number;
  className?: string;
};

const ROWS: string[][] = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
];

/**
 * Touch-friendly numeric keypad. 44×44 minimum buttons via `size="touch"`.
 *
 * Controlled component: parent owns the string value. Treat value as a raw
 * digit string ("1234" or "12.34") — parse with parseFloat at submit time.
 */
export function NumberPad({
  value,
  onChange,
  integerOnly = false,
  maxLength = 12,
  className,
}: NumberPadProps) {
  const append = (ch: string) => {
    if (value.length >= maxLength) return;
    if (ch === '.' && value.includes('.')) return;
    if (ch === '.' && value.length === 0) {
      onChange('0.');
      return;
    }
    onChange(value + ch);
  };

  const backspace = () => onChange(value.slice(0, -1));

  return (
    <div
      className={cn('grid grid-cols-3 gap-2', className)}
      role="group"
      aria-label="Numeric keypad"
    >
      {ROWS.flat().map((digit) => (
        <Button
          key={digit}
          type="button"
          variant="outline"
          size="touch"
          onClick={() => append(digit)}
          className="text-lg font-semibold"
        >
          {digit}
        </Button>
      ))}
      <Button
        type="button"
        variant="outline"
        size="touch"
        onClick={() => append('.')}
        disabled={integerOnly}
        aria-label="Decimal point"
        className="text-lg font-semibold"
      >
        .
      </Button>
      <Button
        type="button"
        variant="outline"
        size="touch"
        onClick={() => append('0')}
        className="text-lg font-semibold"
      >
        0
      </Button>
      <Button
        type="button"
        variant="outline"
        size="touch-icon"
        onClick={backspace}
        disabled={value.length === 0}
        aria-label="Backspace"
      >
        <Delete />
      </Button>
    </div>
  );
}
