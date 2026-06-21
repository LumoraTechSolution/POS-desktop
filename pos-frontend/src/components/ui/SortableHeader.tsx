'use client';

import { ArrowUpDown, ArrowUp, ArrowDown } from 'lucide-react';
import { TableHead } from '@/components/ui/table';

export type SortDirection = 'asc' | 'desc' | null;

interface SortableHeaderProps {
  label: string;
  sortKey: string;
  currentSort: string | null;
  currentDirection: SortDirection;
  onSort: (key: string, direction: SortDirection) => void;
  className?: string;
}

export function SortableHeader({
  label,
  sortKey,
  currentSort,
  currentDirection,
  onSort,
  className = '',
}: SortableHeaderProps) {
  const isActive = currentSort === sortKey;

  const handleClick = () => {
    if (!isActive) {
      onSort(sortKey, 'asc');
    } else if (currentDirection === 'asc') {
      onSort(sortKey, 'desc');
    } else {
      onSort(sortKey, null); // Reset sort
    }
  };

  return (
    <TableHead
      className={`text-muted-foreground font-semibold cursor-pointer select-none hover:text-foreground transition-colors group ${className}`}
      onClick={handleClick}
    >
      <div className="flex items-center gap-1.5">
        {label}
        <span className="inline-flex">
          {isActive && currentDirection === 'asc' ? (
            <ArrowUp size={14} className="text-primary" />
          ) : isActive && currentDirection === 'desc' ? (
            <ArrowDown size={14} className="text-primary" />
          ) : (
            <ArrowUpDown size={14} className="opacity-0 group-hover:opacity-40 transition-opacity" />
          )}
        </span>
      </div>
    </TableHead>
  );
}
