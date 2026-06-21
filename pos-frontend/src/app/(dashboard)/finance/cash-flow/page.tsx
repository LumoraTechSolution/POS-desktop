"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { financeService } from "@/services/financeService";
import { QK } from "@/lib/queryKeys";
import { Card, CardContent } from "@/components/ui/card";
import { Activity, Loader2, ArrowDownRight, ArrowUpRight } from "lucide-react";
import { PeriodPicker, type Period } from "../PeriodPicker";
import { BranchFilter } from "@/components/reports/BranchFilter";
import { cn } from "@/lib/utils";

const money = (n: number) =>
  (Number.isFinite(n) ? n : 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

function thisYearPeriod(): Period {
  const now = new Date();
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  return { start: iso(new Date(now.getFullYear(), 0, 1)), end: iso(new Date(now.getFullYear(), 11, 31)) };
}

export default function CashFlowPage() {
  // Default to the year so the monthly series and runway are meaningful.
  const [period, setPeriod] = useState<Period>(thisYearPeriod);
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
    queryKey: [...QK.financeCashFlow, period.start, period.end, branchId],
    queryFn: () => financeService.getCashFlow(period.start, period.end, branchId),
  });

  const maxBar = data ? Math.max(1, ...data.series.map((s) => Math.max(s.inflow, s.outflow))) : 1;

  return (
    <div className="p-8 space-y-6 animate-in fade-in duration-500">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <Activity className="text-primary" size={24} />
            <h1 className="text-3xl font-bold tracking-tight">Cash Flow</h1>
          </div>
          <p className="text-muted-foreground">Money in (sales) vs money out (expenses + inventory), by month.</p>
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <BranchFilter value={branchId} onChange={setBranchId} />
          <PeriodPicker value={period} onChange={setPeriod} />
        </div>
      </div>

      {isLoading || !data ? (
        <div className="flex justify-center py-20"><Loader2 className="animate-spin text-primary" size={32} /></div>
      ) : (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <Stat label="Money In" value={money(data.totalInflow)} tone="success" icon={<ArrowUpRight size={18} />} />
            <Stat label="Money Out" value={money(data.totalOutflow)} tone="destructive" icon={<ArrowDownRight size={18} />} />
            <Stat label="Net Cash Flow" value={money(data.netCashFlow)} tone={data.netCashFlow >= 0 ? "success" : "destructive"} />
            <Stat
              label="Runway"
              value={
                data.avgMonthlyNetBurn == null
                  ? "Cash-positive"
                  : data.runwayMonths == null
                    ? "—"
                    : `${data.runwayMonths} mo`
              }
              tone={data.avgMonthlyNetBurn == null ? "success" : "warning"}
              hint={data.avgMonthlyNetBurn != null ? `Burn ~${money(data.avgMonthlyNetBurn)}/mo (est.)` : undefined}
            />
          </div>

          <Card className="bg-background border-border">
            <CardContent className="p-6">
              <p className="text-sm font-semibold text-foreground mb-4">Monthly breakdown</p>
              <div className="space-y-3">
                {data.series.map((s) => (
                  <div key={s.label} className="grid grid-cols-[64px_1fr_110px] items-center gap-3">
                    <span className="text-xs text-muted-foreground tabular-nums">{s.label}</span>
                    <div className="space-y-1">
                      <div className="h-2.5 rounded-full bg-success/70" style={{ width: `${(s.inflow / maxBar) * 100}%` }} />
                      <div className="h-2.5 rounded-full bg-destructive/70" style={{ width: `${(s.outflow / maxBar) * 100}%` }} />
                    </div>
                    <span className={cn("text-right text-sm font-semibold tabular-nums", s.net >= 0 ? "text-success" : "text-destructive")}>
                      {s.net >= 0 ? money(s.net) : `(${money(Math.abs(s.net))})`}
                    </span>
                  </div>
                ))}
              </div>
              <div className="flex gap-4 mt-4 text-xs text-muted-foreground">
                <span className="flex items-center gap-1"><span className="w-3 h-2 rounded-full bg-success/70 inline-block" /> In</span>
                <span className="flex items-center gap-1"><span className="w-3 h-2 rounded-full bg-destructive/70 inline-block" /> Out</span>
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

function Stat({ label, value, tone, icon, hint }: {
  label: string; value: string; tone: "success" | "destructive" | "warning"; icon?: React.ReactNode; hint?: string;
}) {
  const toneCls = tone === "success" ? "text-success" : tone === "destructive" ? "text-destructive" : "text-warning";
  return (
    <Card className="bg-background border-border">
      <CardContent className="p-5">
        <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider flex items-center gap-1">
          {icon} {label}
        </p>
        <p className={cn("text-2xl font-bold tabular-nums", toneCls)}>{value}</p>
        {hint && <p className="text-[11px] text-muted-foreground mt-0.5">{hint}</p>}
      </CardContent>
    </Card>
  );
}
