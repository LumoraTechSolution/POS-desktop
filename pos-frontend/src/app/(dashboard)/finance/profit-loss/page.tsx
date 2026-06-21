"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { financeService } from "@/services/financeService";
import { QK } from "@/lib/queryKeys";
import { Card, CardContent } from "@/components/ui/card";
import { TrendingUp, Loader2 } from "lucide-react";
import { PeriodPicker, thisMonth, type Period } from "../PeriodPicker";
import { BranchFilter } from "@/components/reports/BranchFilter";
import { cn } from "@/lib/utils";

const money = (n: number) =>
  (Number.isFinite(n) ? n : 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export default function ProfitLossPage() {
  const [period, setPeriod] = useState<Period>(thisMonth);
  const router = useRouter();
  const searchParams = useSearchParams();
  const branchId = searchParams.get("branch") ?? undefined;

  const setBranchId = (id: string | undefined) => {
    const params = new URLSearchParams(searchParams.toString());
    if (id) params.set("branch", id);
    else params.delete("branch");
    const qs = params.toString();
    router.replace(qs ? `?${qs}` : "?", { scroll: false });
  };

  const { data, isLoading } = useQuery({
    queryKey: [...QK.financePnl, period.start, period.end, branchId],
    queryFn: () => financeService.getProfitLoss(period.start, period.end, branchId),
  });

  return (
    <div className="p-8 space-y-6 animate-in fade-in duration-500">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <TrendingUp className="text-primary" size={24} />
            <h1 className="text-3xl font-bold tracking-tight">Profit &amp; Loss</h1>
          </div>
          <p className="text-muted-foreground">Revenue minus cost of goods and operating expenses.</p>
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <BranchFilter value={branchId} onChange={setBranchId} />
          <PeriodPicker value={period} onChange={setPeriod} />
        </div>
      </div>

      {isLoading || !data ? (
        <div className="flex justify-center py-20">
          <Loader2 className="animate-spin text-primary" size={32} />
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <Card className="bg-background border-border lg:col-span-2">
            <CardContent className="p-6 space-y-1">
              <Line label="Revenue" value={data.revenue} bold />
              <Line label="Cost of Goods Sold" value={-data.costOfGoodsSold} muted />
              <Divider />
              <Line label="Gross Profit" value={data.grossProfit} bold suffix={`${data.grossMarginPct}% margin`} />
              <Line label="Operating Expenses" value={-data.operatingExpenses} muted />
              <Divider />
              <Line
                label="Net Profit"
                value={data.netProfit}
                bold
                big
                suffix={`${data.netMarginPct}% margin`}
                positive={data.netProfit >= 0}
              />
            </CardContent>
          </Card>

          <Card className="bg-background border-border">
            <CardContent className="p-6">
              <p className="text-sm font-semibold text-foreground mb-3">Expense Breakdown</p>
              {data.expenseBreakdown.length === 0 ? (
                <p className="text-sm text-muted-foreground">No operating expenses in this period.</p>
              ) : (
                <div className="space-y-2">
                  {data.expenseBreakdown.map((c) => (
                    <div key={c.categoryId} className="flex justify-between text-sm">
                      <span className="text-muted-foreground">{c.categoryName}</span>
                      <span className="font-medium tabular-nums">{money(c.amount)}</span>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}

function Line({
  label, value, bold, muted, big, suffix, positive,
}: {
  label: string; value: number; bold?: boolean; muted?: boolean; big?: boolean; suffix?: string; positive?: boolean;
}) {
  const negative = value < 0;
  return (
    <div className="flex items-center justify-between py-1.5">
      <span className={cn("text-foreground", bold && "font-semibold", muted && "text-muted-foreground", big && "text-lg")}>
        {label}
      </span>
      <div className="flex items-center gap-3">
        {suffix && <span className="text-xs text-muted-foreground">{suffix}</span>}
        <span
          className={cn(
            "tabular-nums",
            bold && "font-bold",
            big && "text-xl",
            positive === true && "text-success",
            positive === false && "text-destructive",
            positive === undefined && negative && "text-muted-foreground"
          )}
        >
          {negative ? `(${money(Math.abs(value))})` : money(value)}
        </span>
      </div>
    </div>
  );
}

function Divider() {
  return <div className="border-t border-border my-1" />;
}
