"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { reportService } from "@/services/reportService";
import { StockVarianceReport } from "@/types/report";
import { format } from "date-fns";
import { Download } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { DateRangePicker } from "@/components/ui/date-range-picker";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Pagination } from "@/components/ui/pagination";
import { downloadCsv } from "@/lib/csv";

const PAGE_SIZE = 15;
const fc = (val: number) =>
  new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR" }).format(val);

interface DateRange { start: string; end: string }
interface Props { dateRange: DateRange; onDateChange: (r: DateRange) => void; branchId?: string }

export function StockVarianceTab({ dateRange, onDateChange, branchId }: Props) {
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery<StockVarianceReport>({
    queryKey: ["reports", "stock-variance", dateRange, branchId],
    queryFn: () => reportService.getStockVariance(dateRange.start, dateRange.end, branchId),
  });

  const products = data?.products ?? [];
  const paged = products.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const totalPages = Math.max(1, Math.ceil(products.length / PAGE_SIZE));

  const exportCSV = () => {
    if (!products.length) return;
    const headers = ["Product", "SKU", "Reconciled", "Damaged", "Manual Stock-Out", "Total Lost", "Cost Impact"];
    const rows = products.map(p => [
      p.productName, p.sku ?? "",
      p.reconciledUnits, p.damagedUnits, p.stockOutUnits,
      p.totalLost, p.costImpact,
    ]);
    downloadCsv(`report-stock-variance-${format(new Date(), "yyyyMMdd")}.csv`, headers, rows);
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {[
          { label: "Total Units Lost", value: (data?.totalUnitsLost ?? 0).toLocaleString(), cls: "text-destructive" },
          { label: "Estimated Cost Impact", value: fc(data?.estimatedCostLoss ?? 0), cls: "text-warning" },
          { label: "Products Affected", value: (data?.productsAffected ?? 0).toLocaleString(), cls: "text-foreground" },
        ].map(stat => (
          <Card key={stat.label} className="bg-card/50 border-border p-3">
            <p className="text-[10px] uppercase tracking-wider text-muted-foreground">{stat.label}</p>
            <p className={`text-xl font-bold ${stat.cls}`}>{isLoading ? "..." : stat.value}</p>
          </Card>
        ))}
      </div>

      <Card className="bg-card/50 border-border backdrop-blur-sm">
        <CardHeader className="space-y-3 pb-3">
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div className="space-y-1">
              <CardTitle>Stock Variance &amp; Shrinkage</CardTitle>
              <CardDescription>
                Unexpected outflows from manual reconciliation, damage write-offs,
                and manual stock-outs. Sales deductions are excluded.
              </CardDescription>
            </div>
            <Button variant="outline" size="sm" className="border-border text-foreground gap-1" onClick={exportCSV}>
              <Download size={14} /> CSV
            </Button>
          </div>
          <DateRangePicker value={dateRange} onChange={onDateChange} onRangeChange={() => setPage(0)} />
        </CardHeader>
        <CardContent>
          <div className="rounded-md border border-border bg-card/40">
            <Table>
              <TableHeader className="bg-muted/50">
                <TableRow>
                  <TableHead>Product</TableHead>
                  <TableHead>SKU</TableHead>
                  <TableHead className="text-center">Reconciled</TableHead>
                  <TableHead className="text-center">Damaged</TableHead>
                  <TableHead className="text-center">Manual Stock-Out</TableHead>
                  <TableHead className="text-center">Total Lost</TableHead>
                  <TableHead className="text-right">Cost Impact</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-10 animate-pulse text-muted-foreground">
                      Loading stock variance...
                    </TableCell>
                  </TableRow>
                ) : !products.length ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-10 text-muted-foreground">
                      No shrinkage events recorded for the selected period.
                    </TableCell>
                  </TableRow>
                ) : (
                  paged.map(p => (
                    <TableRow key={p.productId} className="hover:bg-muted/50">
                      <TableCell className="font-medium text-foreground">{p.productName}</TableCell>
                      <TableCell className="text-muted-foreground font-mono text-xs">{p.sku || "—"}</TableCell>
                      <TableCell className="text-center text-foreground">{p.reconciledUnits}</TableCell>
                      <TableCell className="text-center text-destructive">{p.damagedUnits}</TableCell>
                      <TableCell className="text-center text-foreground">{p.stockOutUnits}</TableCell>
                      <TableCell className="text-center font-bold text-destructive">{p.totalLost}</TableCell>
                      <TableCell className="text-right font-semibold text-warning">{fc(p.costImpact)}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            onPageChange={setPage}
            isLoading={isLoading}
          />
        </CardContent>
      </Card>
    </div>
  );
}
