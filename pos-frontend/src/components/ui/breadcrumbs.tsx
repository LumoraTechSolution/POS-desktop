'use client';

import Link from 'next/link';
import { ChevronRight, Home } from 'lucide-react';
import { cn } from '@/lib/utils';

export type Crumb = {
  label: string;
  href?: string;
};

type BreadcrumbsProps = {
  items: Crumb[];
  /** When true, prepend a Home icon linking to "/". Defaults true. */
  showHome?: boolean;
  homeHref?: string;
  className?: string;
};

export function Breadcrumbs({
  items,
  showHome = true,
  homeHref = '/overview',
  className,
}: BreadcrumbsProps) {
  const list: Crumb[] = showHome ? [{ label: 'Home', href: homeHref }, ...items] : items;

  return (
    <nav aria-label="Breadcrumb" className={cn('text-sm', className)}>
      <ol className="flex items-center gap-1.5 text-muted-foreground">
        {list.map((item, idx) => {
          const isLast = idx === list.length - 1;
          const isHome = showHome && idx === 0;
          const content = isHome ? (
            <Home size={14} aria-label="Home" />
          ) : (
            <span className="truncate max-w-[12rem]">{item.label}</span>
          );

          return (
            <li key={`${item.label}-${idx}`} className="flex items-center gap-1.5">
              {idx > 0 && <ChevronRight size={14} className="text-muted-foreground/60 shrink-0" />}
              {isLast || !item.href ? (
                <span
                  className={cn('flex items-center', isLast && 'text-foreground font-medium')}
                  aria-current={isLast ? 'page' : undefined}
                >
                  {content}
                </span>
              ) : (
                <Link
                  href={item.href}
                  className="flex items-center hover:text-foreground transition-colors"
                >
                  {content}
                </Link>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
