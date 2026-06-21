"use client";

import { Button } from "@/components/ui/button";
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  isLoading?: boolean;
}

export function Pagination({
  currentPage,
  totalPages,
  onPageChange,
  isLoading = false,
}: PaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-center gap-2 py-6 animate-in fade-in slide-in-from-bottom-2 duration-500">
      <div className="flex items-center gap-1">
        <Button
          variant="outline"
          size="icon"
          aria-label="First page"
          title="First page"
          className="h-8 w-8 border-border bg-card/50 text-muted-foreground hover:text-primary hover:border-primary/50 transition-all"
          disabled={currentPage === 0 || isLoading}
          onClick={() => onPageChange(0)}
        >
          <ChevronsLeft size={16} />
        </Button>
        <Button
          variant="outline"
          size="icon"
          aria-label="Previous page"
          title="Previous page"
          className="h-8 w-8 border-border bg-card/50 text-muted-foreground hover:text-primary hover:border-primary/50 transition-all"
          disabled={currentPage === 0 || isLoading}
          onClick={() => onPageChange(currentPage - 1)}
        >
          <ChevronLeft size={16} />
        </Button>
      </div>

      <div className="flex items-center px-4 py-1 rounded-md bg-card/80 border border-border shadow-inner">
        <span className="text-xs font-mono tracking-wider tabular-nums">
          <span className="text-primary font-bold">{currentPage + 1}</span>
          <span className="text-muted-foreground mx-2">/</span>
          <span className="text-muted-foreground">{totalPages}</span>
        </span>
      </div>

      <div className="flex items-center gap-1">
        <Button
          variant="outline"
          size="icon"
          aria-label="Next page"
          title="Next page"
          className="h-8 w-8 border-border bg-card/50 text-muted-foreground hover:text-primary hover:border-primary/50 transition-all"
          disabled={currentPage + 1 >= totalPages || isLoading}
          onClick={() => onPageChange(currentPage + 1)}
        >
          <ChevronRight size={16} />
        </Button>
        <Button
          variant="outline"
          size="icon"
          aria-label="Last page"
          title="Last page"
          className="h-8 w-8 border-border bg-card/50 text-muted-foreground hover:text-primary hover:border-primary/50 transition-all"
          disabled={currentPage + 1 >= totalPages || isLoading}
          onClick={() => onPageChange(totalPages - 1)}
        >
          <ChevronsRight size={16} />
        </Button>
      </div>
    </div>
  );
}
