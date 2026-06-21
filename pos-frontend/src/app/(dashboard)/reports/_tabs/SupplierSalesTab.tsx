"use client";

import { useState, Fragment } from "react";
import { useQuery } from "@tanstack/react-query";
import { reportService } from "@/services/reportService";
import { SoldItemsBySupplierReport, SoldItemsBySupplierRecord } from "@/types/report";
import { format } from "date-fns";
import { Download, ChevronRight, ChevronDown } from "lucide-react";
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

export function SupplierSalesTab({ dateRange, onDateChange }: Props) {
  const [page, setPage] = useState(0);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

  const toggle = (key: string) => setExpanded(prev => ({ ...prev, [key]: !prev[key] }));

  const { data, isLoading } = useQuery<SoldItemsBySupplierReport>({
    queryKey: ["reports", "sold-by-supplier", dateRange],
    queryFn: () => reportService.getSoldItemsBySupplier(dateRange.start, dateRange.end),
  });

  const suppliers = data?.suppliers ?? [];
  const paged = suppliers.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const totalPages = Math.max(1, Math.ceil(suppliers.length / PAGE_SIZE));

  const exportCSV = () => {
    if (!suppliers.length) return;
    const headers = ["Supplier", "Supplier Status", "Product", "SKU", "Units Sold", "Revenue", "COGS", "Gross Profit", "Margin %"];
    const rows = suppliers.flatMap(sup =>
      sup.items.map(item => [
        sup.supplierName,
        sup.supplierId == null ? "Unassigned" : sup.supplierActive ? "Active" : "Inactive",
        item.productName, item.sku ?? "",
        item.unitsSold, item.revenue,
        item.cogs, item.grossProfit, item.marginPct,
      ])
    );
    downloadCsv(`report-supplier-sales-${format(new Date(), "yyyyMMdd")}.csv`, headers, rows);
  };

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {[
          { label: "Total Revenue", value: fc(data?.totalRevenue ?? 0), cls: "text-foreground" },
          { label: "Units Sold", value: (data?.totalUnitsSold ?? 0).toLocaleString(), cls: "text-foreground" },
          { label: "Gross Profit", value: fc(data?.totalProfit ?? 0), cls: "text-success" },
          { label: "Overall Margin", value: `${data?.overallMarginPct ?? 0}%`, cls: "text-warning" },
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
              <CardTitle>Sold Items by Supplier</CardTitle>
              <CardDescription>
                Each product is attributed to the supplier of its most recent purchase order.
                Products without a PO history appear under &quot;(Unassigned)&quot;.
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
                  <TableHead className="w-10" />
                  <TableHead>Supplier</TableHead>
                  <TableHead className="text-center">Products</TableHead>
                  <TableHead className="text-center">Units Sold</TableHead>
                  <TableHead className="text-right">Revenue</TableHead>
                  <TableHead className="text-right">Gross Profit</TableHead>
                  <TableHead className="text-right">Margin</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-10 animate-pulse text-muted-foreground">
                      Loading supplier sales...
                    </TableCell>
                  </TableRow>
                ) : !suppliers.length ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-10 text-muted-foreground">
                      No sales for the selected period.
                    </TableCell>
                  </TableRow>
                ) : (
                  paged.map((sup: SoldItemsBySupplierRecord) => {
                    const rowKey = sup.supplierId ?? "unassigned";
                    const isUnassigned = sup.supplierId == null;
                    return (
                      <Fragment key={rowKey}>
                        <TableRow
                          className="hover:bg-muted/50 cursor-pointer"
                          onClick={() => toggle(rowKey)}
                        >
                          <TableCell className="w-10 px-3">
                            {expanded[rowKey]
                              ? <ChevronDown size={16} className="text-primary" />
                              : <ChevronRight size={16} className="text-muted-foreground" />}
                          </TableCell>
                          <TableCell>
                            <div className="flex items-center gap-2">
                              <span className={`font-medium ${isUnassigned ? "text-muted-foreground italic" : "text-foreground"}`}>
                                {sup.supplierName}
                              </span>
                              {isUnassigned && (
                                <Badge variant="outline" className="border-border text-muted-foreground text-[10px] uppercase tracking-wider">
                                  No PO history
                                </Badge>
                              )}
                              {!isUnassigned && !sup.supplierActive && (
                                <Badge variant="outline" className="border-amber-800 text-warning bg-warning/5 text-[10px] uppercase tracking-wider">
                                  Inactive
                                </Badge>
                              )}
                            </div>
                          </TableCell>
                          <TableCell className="text-center">
                            <Badge variant="outline" className="border-border">{sup.productCount}</Badge>
                          </TableCell>
                          <TableCell className="text-center text-foreground">{sup.totalUnitsSold.toLocaleString()}</TableCell>
                          <TableCell className="text-right font-bold text-foreground">{fc(sup.totalRevenue)}</TableCell>
                          <TableCell className="text-right text-success">{fc(sup.grossProfit)}</TableCell>
                          <TableCell className="text-right">
                            <span className={`font-bold ${sup.marginPct >= 30 ? "text-success" : sup.marginPct >= 15 ? "text-warning" : "text-destructive"}`}>
                              {sup.marginPct}%
                            </span>
                          </TableCell>
                        </TableRow>
                        {expanded[rowKey] && (
                          <TableRow>
                            <TableCell colSpan={7} className="p-0 border-b border-border">
                              <div className="bg-background/80 px-6 py-4 ml-8 mr-4 my-2 rounded-lg border border-border/50">
                                <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">
                                  Products sold for this supplier
                                </h4>
                                <table className="w-full text-sm">
                                  <thead>
                                    <tr className="text-muted-foreground text-xs uppercase">
                                      <th className="text-left pb-2 pr-4">Product</th>
                                      <th className="text-left pb-2 pr-4">SKU</th>
                                      <th className="text-center pb-2 pr-4">Units</th>
                                      <th className="text-right pb-2 pr-4">Revenue</th>
                                      <th className="text-right pb-2 pr-4">COGS</th>
                                      <th className="text-right pb-2 pr-4">Gross Profit</th>
                                      <th className="text-right pb-2">Margin</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {sup.items.map(item => (
                                      <tr key={item.productId} className="border-t border-border/30">
                                        <td className="py-2 pr-4 text-foreground font-medium">{item.productName}</td>
                                        <td className="py-2 pr-4 text-muted-foreground font-mono text-xs">{item.sku || "—"}</td>
                                        <td className="py-2 pr-4 text-center text-foreground">{item.unitsSold}</td>
                                        <td className="py-2 pr-4 text-right text-foreground">{fc(item.revenue)}</td>
                                        <td className="py-2 pr-4 text-right text-destructive">{fc(item.cogs)}</td>
                                        <td className="py-2 pr-4 text-right font-semibold text-success">{fc(item.grossProfit)}</td>
                                        <td className="py-2 text-right">
                                          <span className={`font-bold ${item.marginPct >= 30 ? "text-success" : item.marginPct >= 15 ? "text-warning" : "text-destructive"}`}>
                                            {item.marginPct}%
                                          </span>
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            </TableCell>
                          </TableRow>
                        )}
                      </Fragment>
                    );
                  })
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
