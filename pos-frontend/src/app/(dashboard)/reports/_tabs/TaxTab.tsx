"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { reportService } from "@/services/reportService";
import { TaxSummaryReport, TaxLineItem } from "@/types/report";
import { format } from "date-fns";
import { Download, Percent } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { DateRangePicker } from "@/components/ui/date-range-picker";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Pagination } from "@/components/ui/pagination";
import { downloadCsv } from "@/lib/csv";

const PAGE_SIZE = 15;
const fc = (val: number) =>
  new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR" }).format(val);

interface DateRange { start: string; end: string }
interface Props { dateRange: DateRange; onDateChange: (r: DateRange) => void }

export function TaxTab({ dateRange, onDateChange }: Props) {
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery<TaxSummaryReport>({
    queryKey: ["reports", "tax-summary", dateRange],
    queryFn: () => reportService.getTaxSummary(dateRange.start, dateRange.end),
  });

  const breakdown = data?.breakdown ?? [];
  const paged = breakdown.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const totalPages = Math.max(1, Math.ceil(breakdown.length / PAGE_SIZE));

  const exportCSV = () => {
    if (!breakdown.length) return;
    const headers = ["Payment Method", "Transactions", "Gross Revenue", "Tax Collected"];
    const rows = breakdown.map(t => [
      t.paymentMethod, t.transactionCount, t.grossRevenue, t.taxCollected,
    ]);
    downloadCsv(`report-tax-${format(new Date(), "yyyyMMdd")}.csv`, headers, rows);
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-6">
        <Card className="bg-card/50 border-border">
          <CardHeader className="pb-2">
            <CardDescription>Total Tax Collected</CardDescription>
            <CardTitle className="text-3xl font-bold text-primary flex items-center gap-2">
              <Percent size={28} />
              {isLoading ? "..." : fc(data?.totalTaxCollected ?? 0)}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground">Across all payment methods for the selected period.</p>
          </CardContent>
        </Card>
        <Card className="bg-card/50 border-border">
          <CardHeader className="pb-2">
            <CardDescription>Tax-Generating Transactions</CardDescription>
            <CardTitle className="text-3xl font-bold">{isLoading ? "..." : (data?.totalTransactions ?? 0)}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-xs text-muted-foreground">Total number of sales in this period.</p>
          </CardContent>
        </Card>
      </div>

      <Card className="bg-card/50 border-border">
        <CardHeader className="flex flex-row items-start justify-between gap-4 flex-wrap pb-3">
          <div className="space-y-1">
            <CardTitle>Breakdown by Payment Method</CardTitle>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="sm" className="border-border text-foreground gap-1" onClick={exportCSV}>
              <Download size={14} /> CSV
            </Button>
          </div>
        </CardHeader>
        <CardHeader className="pt-0 pb-3">
          <DateRangePicker value={dateRange} onChange={onDateChange} onRangeChange={() => setPage(0)} />
        </CardHeader>
        <CardContent>
          <div className="rounded-md border border-border bg-card/40">
            <Table>
              <TableHeader className="bg-muted/50">
                <TableRow>
                  <TableHead>Payment Method</TableHead>
                  <TableHead className="text-center">Transactions</TableHead>
                  <TableHead className="text-right">Gross Revenue</TableHead>
                  <TableHead className="text-right">Tax Collected</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center py-10 animate-pulse text-muted-foreground">Loading...</TableCell>
                  </TableRow>
                ) : !breakdown.length ? (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center py-10 text-muted-foreground">No tax data for this period.</TableCell>
                  </TableRow>
                ) : (
                  paged.map((row: TaxLineItem) => (
                    <TableRow key={row.paymentMethod} className="hover:bg-muted/50">
                      <TableCell className="font-medium">{row.paymentMethod}</TableCell>
                      <TableCell className="text-center">
                        <Badge variant="outline" className="border-border">{row.transactionCount}</Badge>
                      </TableCell>
                      <TableCell className="text-right text-foreground">{fc(row.grossRevenue)}</TableCell>
                      <TableCell className="text-right font-bold text-primary">{fc(row.taxCollected)}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
          <Pagination currentPage={page} totalPages={totalPages} onPageChange={setPage} isLoading={isLoading} />
        </CardContent>
      </Card>
    </div>
  );
}
