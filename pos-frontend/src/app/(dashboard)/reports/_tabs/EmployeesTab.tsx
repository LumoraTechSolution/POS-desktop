"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { reportService } from "@/services/reportService";
import { EmployeePerformanceRecord } from "@/types/report";
import { Page } from "@/types/common";
import { format } from "date-fns";
import { Download } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { DateRangePicker } from "@/components/ui/date-range-picker";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Pagination } from "@/components/ui/pagination";
import { downloadCsv, fetchAllPages } from "@/lib/csv";
import { toast } from "sonner";

const PAGE_SIZE = 15;
const fc = (val: number) =>
  new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR" }).format(val);

interface DateRange { start: string; end: string }
interface Props { dateRange: DateRange; onDateChange: (r: DateRange) => void; branchId?: string }

export function EmployeesTab({ dateRange, onDateChange, branchId }: Props) {
  const [page, setPage] = useState(0);
  const [isExporting, setIsExporting] = useState(false);

  const { data, isLoading } = useQuery<Page<EmployeePerformanceRecord>>({
    queryKey: ["reports", "employee-performance", dateRange, page, branchId],
    queryFn: () => reportService.getEmployeePerformance(dateRange.start, dateRange.end, page, PAGE_SIZE, branchId),
  });

  const exportCSV = async () => {
    if (!data?.content?.length) return;
    setIsExporting(true);
    try {
      const all = await fetchAllPages(
        (p, s) => reportService.getEmployeePerformance(dateRange.start, dateRange.end, p, s, branchId),
      );
      const headers = ["Employee", "Email", "Transactions", "Revenue", "Avg Basket", "Discounts"];
      const rows = all.map(emp => [
        emp.employeeName, emp.email, emp.transactionCount,
        emp.totalRevenue, emp.avgTransactionValue, emp.totalDiscount,
      ]);
      downloadCsv(`report-employees-${format(new Date(), "yyyyMMdd")}.csv`, headers, rows);
    } catch {
      toast.error("Export failed");
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <Card className="bg-card/50 border-border backdrop-blur-sm">
      <CardHeader className="space-y-3 pb-3">
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div className="space-y-1">
            <CardTitle>Employee Performance</CardTitle>
            <CardDescription>Cashier revenue, transaction count, and averages for the selected period.</CardDescription>
          </div>
          <Button variant="outline" size="sm" className="border-border text-foreground gap-1" onClick={exportCSV} disabled={isExporting}>
            <Download size={14} /> {isExporting ? "Exporting…" : "CSV"}
          </Button>
        </div>
        <DateRangePicker value={dateRange} onChange={onDateChange} onRangeChange={() => setPage(0)} />
      </CardHeader>
      <CardContent>
        <div className="rounded-md border border-border bg-card/40">
          <Table>
            <TableHeader className="bg-muted/50">
              <TableRow>
                <TableHead>Employee</TableHead>
                <TableHead className="text-center">Transactions</TableHead>
                <TableHead className="text-right">Total Revenue</TableHead>
                <TableHead className="text-right">Avg Basket</TableHead>
                <TableHead className="text-right">Total Discount Given</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={5} className="text-center py-10 animate-pulse text-muted-foreground">Loading...</TableCell>
                </TableRow>
              ) : !data?.content?.length ? (
                <TableRow>
                  <TableCell colSpan={5} className="text-center py-10 text-muted-foreground">No data for this period.</TableCell>
                </TableRow>
              ) : (
                data.content.map((emp, i) => (
                  <TableRow key={emp.userId} className="hover:bg-muted/50">
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-primary/20 text-primary flex items-center justify-center text-xs font-bold flex-shrink-0">
                          {i + 1}
                        </div>
                        <div>
                          <div className="font-medium text-foreground">{emp.employeeName}</div>
                          <div className="text-xs text-muted-foreground">{emp.email}</div>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-center">
                      <Badge variant="outline" className="border-border">{emp.transactionCount}</Badge>
                    </TableCell>
                    <TableCell className="text-right font-bold text-success">{fc(emp.totalRevenue)}</TableCell>
                    <TableCell className="text-right text-foreground">{fc(emp.avgTransactionValue)}</TableCell>
                    <TableCell className="text-right text-warning">{fc(emp.totalDiscount)}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
        <Pagination
          currentPage={page}
          totalPages={data?.totalPages ?? 0}
          onPageChange={setPage}
          isLoading={isLoading}
        />
      </CardContent>
    </Card>
  );
}
