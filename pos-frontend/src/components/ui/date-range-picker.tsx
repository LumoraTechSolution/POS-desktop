'use client';

import React from 'react';
import { Calendar, ArrowRight } from 'lucide-react';
import { format, startOfMonth, endOfMonth, subDays, subMonths } from 'date-fns';
import { cn } from '@/lib/utils';

/**
 * The string format used by every report query — ISO with explicit T-time so
 * Spring's @DateTimeFormat(ISO.DATE_TIME) parses without ambiguity.
 */
export interface DateRangeValue {
  start: string; // "yyyy-MM-dd'T'00:00:00"
  end: string;   // "yyyy-MM-dd'T'23:59:59"
}

interface DateRangePickerProps {
  value: DateRangeValue;
  onChange: (value: DateRangeValue) => void;
  /** Optional handler called whenever the range changes — useful for resetting
   *  pagination back to page 0. */
  onRangeChange?: () => void;
  className?: string;
}

const startOf = (d: Date) => format(d, "yyyy-MM-dd'T'00:00:00");
const endOf = (d: Date) => format(d, "yyyy-MM-dd'T'23:59:59");

interface Preset {
  label: string;
  build: () => DateRangeValue;
}

// Sentinel "epoch" for the All-time preset. Anything earlier than the first
// realistic POS deployment — picked over a far-past date so the date input UI
// still shows a sensible value rather than something like 0001-01-01.
const ALL_TIME_START = "2000-01-01T00:00:00";

const PRESETS: Preset[] = [
  {
    label: 'Today',
    build: () => {
      const now = new Date();
      return { start: startOf(now), end: endOf(now) };
    },
  },
  {
    label: 'Yesterday',
    build: () => {
      const y = subDays(new Date(), 1);
      return { start: startOf(y), end: endOf(y) };
    },
  },
  {
    label: '7 days',
    build: () => {
      const now = new Date();
      return { start: startOf(subDays(now, 6)), end: endOf(now) };
    },
  },
  {
    label: '30 days',
    build: () => {
      const now = new Date();
      return { start: startOf(subDays(now, 29)), end: endOf(now) };
    },
  },
  {
    label: 'This month',
    build: () => {
      const now = new Date();
      return { start: startOf(startOfMonth(now)), end: endOf(now) };
    },
  },
  {
    label: 'Last month',
    build: () => {
      const lastMonth = subMonths(new Date(), 1);
      return { start: startOf(startOfMonth(lastMonth)), end: endOf(endOfMonth(lastMonth)) };
    },
  },
  {
    label: 'All time',
    build: () => ({ start: ALL_TIME_START, end: endOf(new Date()) }),
  },
];

/**
 * Cheap equality on the date-only part so the active-chip highlight survives
 * timezone wobble in the time portion.
 */
function isSameRange(a: DateRangeValue, b: DateRangeValue): boolean {
  return a.start.split('T')[0] === b.start.split('T')[0]
      && a.end.split('T')[0] === b.end.split('T')[0];
}

export function DateRangePicker({ value, onChange, onRangeChange, className }: DateRangePickerProps) {
  const apply = (next: DateRangeValue) => {
    onChange(next);
    onRangeChange?.();
  };

  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-x-3 gap-y-2 w-full',
        className
      )}
    >
      <div className="flex items-center gap-1 flex-wrap">
        {PRESETS.map((p) => {
          const range = p.build();
          const active = isSameRange(value, range);
          return (
            <button
              key={p.label}
              type="button"
              onClick={() => apply(range)}
              className={cn(
                'text-[11px] px-2.5 py-1 rounded-md border transition-colors',
                active
                  ? 'bg-primary/20 border-primary/40 text-primary'
                  : 'bg-muted/50 border-border text-muted-foreground hover:bg-muted hover:text-foreground'
              )}
            >
              {p.label}
            </button>
          );
        })}
      </div>
      <div className="flex items-center gap-2 bg-muted rounded-md p-1 px-3 border border-border ml-auto">
        <Calendar size={14} className="text-muted-foreground" />
        <input
          type="date"
          className="bg-transparent border-none text-sm focus:ring-0 px-1"
          value={value.start.split('T')[0]}
          onChange={(e) => apply({ ...value, start: `${e.target.value}T00:00:00` })}
        />
        <ArrowRight size={12} className="text-muted-foreground" />
        <input
          type="date"
          className="bg-transparent border-none text-sm focus:ring-0 px-1"
          value={value.end.split('T')[0]}
          onChange={(e) => apply({ ...value, end: `${e.target.value}T23:59:59` })}
        />
      </div>
    </div>
  );
}
