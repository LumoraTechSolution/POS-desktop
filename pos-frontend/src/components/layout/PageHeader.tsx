import { type ReactNode } from 'react';
import { cn } from '@/lib/utils';

type PageHeaderProps = {
  title: string;
  description?: ReactNode;
  /** Right-side actions (buttons, dropdowns). */
  actions?: ReactNode;
  /** Optional content above the title — usually breadcrumbs. */
  above?: ReactNode;
  className?: string;
};

/**
 * Standard page header: optional above-slot (breadcrumbs) + title + description
 * + actions. Drop in at the top of every dashboard route for visual consistency.
 */
export function PageHeader({ title, description, actions, above, className }: PageHeaderProps) {
  return (
    <div className={cn('flex flex-col gap-3 pb-6', className)}>
      {above}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">{title}</h1>
          {description && (
            <p className="mt-1 text-sm text-muted-foreground">{description}</p>
          )}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2 shrink-0">{actions}</div>}
      </div>
    </div>
  );
}
