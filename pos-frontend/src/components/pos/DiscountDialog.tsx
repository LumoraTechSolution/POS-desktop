'use client';

import { useEffect, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { CURRENCY } from '@/lib/utils';

type DiscountDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Product name for the header. */
  productName: string;
  /** Line subtotal (basePrice * quantity) — the discount can't exceed this. */
  lineSubtotal: number;
  /** Existing discount amount (0 when adding fresh). */
  currentDiscount: number;
  /** Called with the resolved fixed-amount discount in tenant currency. */
  onApply: (discountAmount: number) => void;
};

type Mode = 'percent' | 'amount';

export function DiscountDialog({
  open,
  onOpenChange,
  productName,
  lineSubtotal,
  currentDiscount,
  onApply,
}: DiscountDialogProps) {
  const [mode, setMode] = useState<Mode>('amount');
  const [percent, setPercent] = useState('');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    if (currentDiscount > 0) {
      setMode('amount');
      setAmount(currentDiscount.toFixed(2));
      setPercent('');
    } else {
      setAmount('');
      setPercent('');
    }
    setError(null);
  }, [open, currentDiscount]);

  const computeAmount = (): number => {
    if (mode === 'percent') {
      const p = parseFloat(percent);
      if (!isFinite(p) || p < 0) return 0;
      return Number(((lineSubtotal * Math.min(p, 100)) / 100).toFixed(2));
    }
    const a = parseFloat(amount);
    return isFinite(a) && a > 0 ? Number(a.toFixed(2)) : 0;
  };

  const previewDiscount = computeAmount();
  const previewTotal = Math.max(0, lineSubtotal - previewDiscount);

  const handleApply = () => {
    const discount = computeAmount();
    if (discount > lineSubtotal) {
      setError(`Discount cannot exceed line subtotal of ${CURRENCY.symbol} ${lineSubtotal.toFixed(2)}`);
      return;
    }
    if (discount < 0) {
      setError('Discount must be a positive value');
      return;
    }
    onApply(discount);
    onOpenChange(false);
  };

  const handleRemove = () => {
    onApply(0);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>Apply discount</DialogTitle>
          <DialogDescription className="truncate">{productName}</DialogDescription>
        </DialogHeader>

        <Tabs value={mode} onValueChange={(v) => { setMode(v as Mode); setError(null); }} className="w-full">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="amount">Amount</TabsTrigger>
            <TabsTrigger value="percent">Percent</TabsTrigger>
          </TabsList>

          <TabsContent value="amount" className="space-y-2 pt-3">
            <label htmlFor="discount-amount" className="text-xs uppercase font-medium text-muted-foreground tracking-wider">
              Discount amount
            </label>
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">{CURRENCY.symbol}</span>
              <Input
                id="discount-amount"
                type="number"
                inputMode="decimal"
                min={0}
                step={0.01}
                value={amount}
                onChange={(e) => { setAmount(e.target.value); setError(null); }}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleApply(); } }}
                placeholder="0.00"
                autoFocus
              />
            </div>
          </TabsContent>

          <TabsContent value="percent" className="space-y-2 pt-3">
            <label htmlFor="discount-percent" className="text-xs uppercase font-medium text-muted-foreground tracking-wider">
              Discount percent
            </label>
            <div className="flex items-center gap-2">
              <Input
                id="discount-percent"
                type="number"
                inputMode="decimal"
                min={0}
                max={100}
                step={0.1}
                value={percent}
                onChange={(e) => { setPercent(e.target.value); setError(null); }}
                onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleApply(); } }}
                placeholder="0"
                autoFocus
              />
              <span className="text-sm text-muted-foreground">%</span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {[5, 10, 15, 20, 25, 50].map((p) => (
                <Button
                  key={p}
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => { setPercent(String(p)); setError(null); }}
                >
                  {p}%
                </Button>
              ))}
            </div>
          </TabsContent>
        </Tabs>

        <div className="rounded-lg border border-border bg-muted/30 p-3 space-y-1 text-sm">
          <div className="flex justify-between text-muted-foreground">
            <span>Line subtotal</span>
            <span className="tabular-nums">{CURRENCY.symbol} {lineSubtotal.toFixed(2)}</span>
          </div>
          <div className="flex justify-between text-destructive">
            <span>Discount</span>
            <span className="tabular-nums">− {CURRENCY.symbol} {previewDiscount.toFixed(2)}</span>
          </div>
          <div className="flex justify-between font-semibold text-foreground pt-1 border-t border-border">
            <span>New line total</span>
            <span className="tabular-nums">{CURRENCY.symbol} {previewTotal.toFixed(2)}</span>
          </div>
        </div>

        {error && (
          <p className="text-sm text-destructive" role="alert">{error}</p>
        )}

        <DialogFooter className="flex-col-reverse sm:flex-row sm:justify-between gap-2">
          {currentDiscount > 0 ? (
            <Button type="button" variant="ghost" onClick={handleRemove}>
              Remove discount
            </Button>
          ) : <div />}
          <div className="flex gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="button" onClick={handleApply} disabled={previewDiscount <= 0 && currentDiscount === 0}>
              Apply
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
