'use client';

import { type ReactNode } from 'react';
import { Search } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

type DataTableToolbarProps = {
  /** Bound to the search input value. */
  searchValue?: string;
  onSearchChange?: (next: string) => void;
  searchPlaceholder?: string;
  /** Render filter chips/dropdowns inline beside the search input. */
  filters?: ReactNode;
  /** Right-side actions (export menu, bulk-action dropdown, Create button). */
  actions?: ReactNode;
  /** Inline "X of Y" result count — render under the toolbar by default. */
  resultsCount?: { shown: number; total: number; label?: string };
  /** Slot rendered when ≥1 row is selected — bulk action bar. */
  selectionBar?: ReactNode;
  className?: string;
};

/**
 * Standard table toolbar: search + filter chips + actions + result count +
 * optional bulk-action bar that appears when rows are selected.
 *
 * Stateless — parent owns search/selection state. Designed to be the single
 * pattern for every data table on the dashboard.
 */
export function DataTableToolbar({
  searchValue,
  onSearchChange,
  searchPlaceholder = 'Search…',
  filters,
  actions,
  resultsCount,
  selectionBar,
  className,
}: DataTableToolbarProps) {
  return (
    <div className={cn('space-y-3', className)}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 flex-wrap items-center gap-2">
          {onSearchChange !== undefined && (
            <div className="relative w-full sm:max-w-xs">
              <Search
                size={16}
                className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
              />
              <Input
                value={searchValue ?? ''}
                onChange={(e) => onSearchChange(e.target.value)}
                placeholder={searchPlaceholder}
                className="pl-9"
                aria-label={searchPlaceholder}
              />
            </div>
          )}
          {filters && <div className="flex flex-wrap items-center gap-2">{filters}</div>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
      {selectionBar && (
        <div className="rounded-md border border-border bg-accent/5 px-3 py-2">{selectionBar}</div>
      )}
      {resultsCount && (
        <p className="text-xs text-muted-foreground" role="status" aria-live="polite">
          Showing {resultsCount.shown} of {resultsCount.total} {resultsCount.label ?? 'results'}
        </p>
      )}
    </div>
  );
}
