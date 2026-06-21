'use client';

import React from 'react';
import { ShieldAlert } from 'lucide-react';

type PlanTier = 'SMALL_BUSINESS' | 'MEDIUM_BUSINESS' | 'ENTERPRISE' | string;

interface PlanBadgeProps {
  plan: PlanTier;
  /** Compact pill (tenant list) vs slightly larger pill (detail header). */
  size?: 'sm' | 'md';
}

const PLAN_STYLES: Record<string, { label: string; classes: string; icon?: React.ReactNode }> = {
  SMALL_BUSINESS: {
    label: 'Small Business',
    classes: 'bg-blue-50 text-blue-700 border-blue-200',
  },
  MEDIUM_BUSINESS: {
    label: 'Medium Business',
    classes: 'bg-purple-50 text-purple-700 border-purple-200',
  },
  ENTERPRISE: {
    label: 'Enterprise',
    classes: 'bg-amber-50 text-amber-700 border-amber-200',
    icon: <ShieldAlert className="h-3 w-3" />,
  },
};

export default function PlanBadge({ plan, size = 'sm' }: PlanBadgeProps) {
  const meta = PLAN_STYLES[plan] ?? {
    label: plan.replace(/_/g, ' '),
    classes: 'bg-muted text-muted-foreground border-border',
  };

  const sizeClasses =
    size === 'md'
      ? 'px-3 py-1.5 text-xs font-bold'
      : 'px-2.5 py-1 text-xs font-semibold';

  return (
    <span
      className={`inline-flex items-center gap-1 w-fit rounded-full border ${sizeClasses} ${meta.classes}`}
    >
      {meta.icon}
      {meta.label}
    </span>
  );
}
