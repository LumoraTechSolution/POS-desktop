"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { reportService } from "@/services/reportService";
import { TopCustomerRecord } from "@/types/report";
import { Page } from "@/types/common";
import { format } from "date-fns";
import { Download, Star } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Pagination } from "@/components/ui/pagination";
import { downloadCsv, fetchAllPages } from "@/lib/csv";
import { toast } from "sonner";

const PAGE_SIZE = 15;
const fc = (val: number) =>
  new Intl.NumberFormat("en-LK", { style: "currency", currency: "LKR" }).format(val);

export function CustomersTab() {
  const [page, setPage] = useState(0);
  const [isExporting, setIsExporting] = useState(false);

  const { data, isLoading } = useQuery<Page<TopCustomerRecord>>({
    queryKey: ["reports", "top-customers", page],
    queryFn: () => reportService.getTopCustomers(page, PAGE_SIZE),
  });

  const exportCSV = async () => {
    if (!data?.content?.length) return;
    setIsExporting(true);
    try {
      const all = await fetchAllPages(
        (p, s) => reportService.getTopCustomers(p, s),
      );
      const headers = ["Customer", "Email", "Phone", "Visits", "Total Spent", "Loyalty Points"];
      const rows = all.map(c => [
        c.customerName, c.email, c.phone,
        c.transactionCount, c.totalSpent, c.loyaltyPoints,
      ]);
      downloadCsv(`report-customers-${format(new Date(), "yyyyMMdd")}.csv`, headers, rows);
    } catch {
      toast.error("Export failed");
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <Card className="bg-card/50 border-border backdrop-blur-sm">
      <CardHeader className="flex flex-row items-start justify-between gap-4 flex-wrap">
        <div>
          <CardTitle>Top Customers by Spend</CardTitle>
          <CardDescription>Highest-value customers across all time.</CardDescription>
        </div>
        <Button variant="outline" size="sm" className="border-border text-foreground gap-1" onClick={exportCSV} disabled={isExporting}>
          <Download size={14} /> {isExporting ? "Exporting…" : "CSV"}
        </Button>
      </CardHeader>
      <CardContent>
        <div className="rounded-md border border-border bg-card/40">
          <Table>
            <TableHeader className="bg-muted/50">
              <TableRow>
                <TableHead>Customer</TableHead>
                <TableHead className="text-center">Visits</TableHead>
                <TableHead className="text-right">Total Spent</TableHead>
                <TableHead className="text-right">Loyalty Points</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={4} className="text-center py-10 animate-pulse text-muted-foreground">Loading...</TableCell>
                </TableRow>
              ) : !data?.content?.length ? (
                <TableRow>
                  <TableCell colSpan={4} className="text-center py-10 text-muted-foreground">No customer data found.</TableCell>
                </TableRow>
              ) : (
                data.content.map((cust, i) => (
                  <TableRow key={cust.customerId} className="hover:bg-muted/50">
                    <TableCell>
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-violet-500/20 text-violet-400 flex items-center justify-center text-xs font-bold flex-shrink-0">
                          {i + 1}
                        </div>
                        <div>
                          <div className="font-medium text-foreground">{cust.customerName}</div>
                          <div className="text-xs text-muted-foreground">{cust.email || cust.phone || "—"}</div>
                        </div>
                      </div>
                    </TableCell>
                    <TableCell className="text-center">
                      <Badge variant="outline" className="border-border">{cust.transactionCount}</Badge>
                    </TableCell>
                    <TableCell className="text-right font-bold text-success">{fc(cust.totalSpent)}</TableCell>
                    <TableCell className="text-right">
                      <span className="inline-flex items-center gap-1 text-warning font-semibold">
                        <Star size={12} fill="currentColor" />{cust.loyaltyPoints.toLocaleString()}
                      </span>
                    </TableCell>
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
