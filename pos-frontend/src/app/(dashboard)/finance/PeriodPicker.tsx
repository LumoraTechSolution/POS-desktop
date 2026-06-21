"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export interface Period {
  start: string; // yyyy-MM-dd
  end: string;
}

const iso = (d: Date) => d.toISOString().slice(0, 10);

export function thisMonth(): Period {
  const now = new Date();
  return {
    start: iso(new Date(now.getFullYear(), now.getMonth(), 1)),
    end: iso(new Date(now.getFullYear(), now.getMonth() + 1, 0)),
  };
}

function lastMonth(): Period {
  const now = new Date();
  return {
    start: iso(new Date(now.getFullYear(), now.getMonth() - 1, 1)),
    end: iso(new Date(now.getFullYear(), now.getMonth(), 0)),
  };
}

function thisYear(): Period {
  const now = new Date();
  return { start: iso(new Date(now.getFullYear(), 0, 1)), end: iso(new Date(now.getFullYear(), 11, 31)) };
}

interface Props {
  value: Period;
  onChange: (p: Period) => void;
}

export function PeriodPicker({ value, onChange }: Props) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="flex gap-1">
        <Button variant="outline" size="sm" onClick={() => onChange(thisMonth())}>This month</Button>
        <Button variant="outline" size="sm" onClick={() => onChange(lastMonth())}>Last month</Button>
        <Button variant="outline" size="sm" onClick={() => onChange(thisYear())}>This year</Button>
      </div>
      <div className="flex items-end gap-2">
        <div className="space-y-1">
          <label className="text-[11px] text-muted-foreground block">From</label>
          <Input type="date" value={value.start} className="bg-card border-border h-9 w-[150px]"
            onChange={(e) => onChange({ ...value, start: e.target.value })} />
        </div>
        <div className="space-y-1">
          <label className="text-[11px] text-muted-foreground block">To</label>
          <Input type="date" value={value.end} className="bg-card border-border h-9 w-[150px]"
            onChange={(e) => onChange({ ...value, end: e.target.value })} />
        </div>
      </div>
    </div>
  );
}
