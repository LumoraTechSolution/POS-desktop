import { cn } from '@/lib/utils';

const PULSE = 'animate-pulse bg-muted/50 rounded';

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn(PULSE, className)} />;
}

export function KPICardSkeleton() {
  return <Skeleton className="h-36 rounded-2xl" />;
}

export function ChartSkeleton({ className }: { className?: string }) {
  return <Skeleton className={cn('h-80 rounded-2xl', className)} />;
}

export function TableRowSkeleton({ columns = 6 }: { columns?: number }) {
  return (
    <tr className="border-b border-border">
      {Array.from({ length: columns }).map((_, i) => (
        <td key={i} className="py-3 pr-4">
          <Skeleton className="h-4 w-full" />
        </td>
      ))}
    </tr>
  );
}

export function TableSkeleton({
  rows = 5,
  columns = 6,
  className,
}: {
  rows?: number;
  columns?: number;
  className?: string;
}) {
  return (
    <div className={cn('overflow-hidden rounded-xl border border-border', className)}>
      <table className="w-full">
        <tbody>
          {Array.from({ length: rows }).map((_, i) => (
            <TableRowSkeleton key={i} columns={columns} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
