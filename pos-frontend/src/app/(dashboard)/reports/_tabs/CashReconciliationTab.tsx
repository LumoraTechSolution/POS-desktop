"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { reportService } from "@/services/reportService";
import { CashReconciliationRecord } from "@/types/report";
import { Page } from "@/types/common";
import { format } from "date-fns";
import { Download, Wallet } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { DateRangePicker } from "@/components/ui/date-range-picker";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Pagination } from "@/components/ui/pagination";
import { downloadCsv, fetchAllPages } from "@/lib/csv";
import { toast } from "sonner";

const PAGE_SIZE = 15;
const fc = (val: number) =>
  new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR" }).format(val);

interface DateRange { start: string; end: string }
interface Props { dateRange: DateRange; onDateChange: (r: DateRange) => void; branchId?: string }

export function CashReconciliationTab({ dateRange, onDateChange, branchId }: Props) {
  const [page, setPage] = useState(0);
  const [isExporting, setIsExporting] = useState(false);

  const { data, isLoading } = useQuery<Page<CashReconciliationRecord>>({
    queryKey: ["reports", "cash-reconciliation", dateRange, page, branchId],
    queryFn: () => reportService.getCashReconciliation(dateRange.start, dateRange.end, page, PAGE_SIZE, branchId),
  });

  const exportCSV = async () => {
    if (!data?.content?.length) return;
    setIsExporting(true);
    try {
      const all = await fetchAllPages(
        (p, s) => reportService.getCashReconciliation(dateRange.start, dateRange.end, p, s, branchId),
      );
      const headers = ["Cashier", "Opened", "Closed", "Opening", "Expected", "Counted", "Variance", "Notes"];
      const rows = all.map(r => [
        r.cashierName,
        format(new Date(r.openedAt), "yyyy-MM-dd HH:mm"),
        r.closedAt ? format(new Date(r.closedAt), "yyyy-MM-dd HH:mm") : "",
        r.openingBalance,
        r.expectedBalance ?? "",
        r.closingBalance ?? "",
        r.variance ?? "",
        r.notes ?? "",
      ]);
      downloadCsv(`report-cash-reconciliation-${format(new Date(), "yyyyMMdd")}.csv`, headers, rows);
    } catch {
      toast.error("Export failed");
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <Card className="bg-card/50 border-border">
      <CardHeader className="pb-2 flex flex-row items-start justify-between gap-4 flex-wrap">
        <div>
          <CardTitle className="text-lg font-semibold text-foreground flex items-center gap-2">
            <Wallet size={18} className="text-primary" /> Cash Reconciliation
          </CardTitle>
          <CardDescription className="text-muted-foreground mt-1">
            Closed shift summaries with opening float, expected balance, counted balance, and variance.
            Sorted by largest absolute variance.
          </CardDescription>
        </div>
        <div className="flex items-center gap-2">
          <DateRangePicker value={dateRange} onChange={onDateChange} onRangeChange={() => setPage(0)} />
          <Button variant="outline" size="sm" className="border-border text-foreground gap-1" onClick={exportCSV} disabled={isExporting}>
            <Download size={14} /> {isExporting ? "Exporting…" : "CSV"}
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow className="border-border">
                <TableHead className="text-muted-foreground">Cashier</TableHead>
                <TableHead className="text-muted-foreground">Opened</TableHead>
                <TableHead className="text-muted-foreground">Closed</TableHead>
                <TableHead className="text-muted-foreground text-right">Opening</TableHead>
                <TableHead className="text-muted-foreground text-right">Expected</TableHead>
                <TableHead className="text-muted-foreground text-right">Counted</TableHead>
                <TableHead className="text-muted-foreground text-right">Variance</TableHead>
                <TableHead className="text-muted-foreground">Notes</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                Array.from({ length: 5 }).map((_, i) => (
                  <TableRow key={i} className="border-border">
                    {Array.from({ length: 8 }).map((_, j) => (
                      <TableCell key={j}>
                        <div className="h-4 bg-muted rounded animate-pulse w-16" />
                      </TableCell>
                    ))}
                  </TableRow>
                ))
              ) : (data?.content?.length ?? 0) === 0 ? (
                <TableRow className="border-border">
                  <TableCell colSpan={8} className="text-center text-muted-foreground py-8">
                    No closed sessions in this date range.
                  </TableCell>
                </TableRow>
              ) : (
                (data?.content ?? [])
                  .slice()
                  .sort((a, b) => Math.abs(b.variance ?? 0) - Math.abs(a.variance ?? 0))
                  .map(row => {
                    const variance = row.variance ?? 0;
                    return (
                      <TableRow key={row.sessionId} className="border-border hover:bg-muted/30">
                        <TableCell className="text-foreground font-medium">{row.cashierName}</TableCell>
                        <TableCell className="text-muted-foreground text-sm">
                          {format(new Date(row.openedAt), "dd/MM/yy HH:mm")}
                        </TableCell>
                        <TableCell className="text-muted-foreground text-sm">
                          {row.closedAt ? format(new Date(row.closedAt), "dd/MM/yy HH:mm") : "—"}
                        </TableCell>
                        <TableCell className="text-foreground text-right font-mono">{fc(row.openingBalance)}</TableCell>
                        <TableCell className="text-foreground text-right font-mono">
                          {row.expectedBalance != null ? fc(row.expectedBalance) : "—"}
                        </TableCell>
                        <TableCell className="text-foreground text-right font-mono">
                          {row.closingBalance != null ? fc(row.closingBalance) : "—"}
                        </TableCell>
                        <TableCell className="text-right font-mono font-semibold">
                          <span className={
                            Math.abs(variance) < 0.01 ? "text-success" :
                            variance > 0 ? "text-warning" : "text-destructive"
                          }>
                            {variance >= 0 ? "+" : ""}{fc(variance)}
                          </span>
                        </TableCell>
                        <TableCell className="text-muted-foreground text-xs max-w-[180px] truncate">
                          {row.notes ?? "—"}
                        </TableCell>
                      </TableRow>
                    );
                  })
              )}
            </TableBody>
          </Table>
        </div>
        {(data?.totalPages ?? 0) > 1 && (
          <div className="mt-4">
            <Pagination
              currentPage={page}
              totalPages={data!.totalPages}
              onPageChange={setPage}
            />
          </div>
        )}
      </CardContent>
    </Card>
  );
}
