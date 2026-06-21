"use client";

import { useQuery } from "@tanstack/react-query";
import { Building2 } from "lucide-react";
import { branchService } from "@/services/branchService";

interface Props {
  /** Selected branch id, or undefined for the all-branches aggregate. */
  value?: string;
  onChange: (branchId: string | undefined) => void;
  className?: string;
}

/**
 * Branch selector for report/finance headers. Reuses {@code GET /branches/me} so it
 * automatically reflects branch restrictions, and hides itself entirely when the user
 * can only see a single branch (nothing to switch between).
 */
export function BranchFilter({ value, onChange, className }: Props) {
  const { data: branches = [] } = useQuery({
    queryKey: ["myBranches"],
    queryFn: branchService.getMyBranches,
  });

  // Nothing to pick from for single-branch users — keep the all-branch aggregate.
  if (branches.length <= 1) return null;

  return (
    <div className={`flex items-center gap-2 ${className ?? ""}`}>
      <Building2 size={15} className="text-muted-foreground" />
      <select
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value || undefined)}
        className="h-9 rounded-md border border-border bg-card px-2 text-sm text-foreground focus:outline-none focus:ring-1 focus:ring-primary"
        aria-label="Filter by branch"
      >
        <option value="">All branches</option>
        {branches.map((b) => (
          <option key={b.id} value={b.id}>
            {b.name}
          </option>
        ))}
      </select>
    </div>
  );
}
