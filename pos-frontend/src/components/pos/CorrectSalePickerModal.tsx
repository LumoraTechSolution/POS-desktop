'use client';

import { useQuery } from '@tanstack/react-query';
import { Loader2, Receipt as ReceiptIcon, ShieldAlert, Clock } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { CURRENCY, cn } from '@/lib/utils';
import { QK } from '@/lib/queryKeys';
import { SaleResponse, salesService } from '@/services/salesService';

/** Sales older than this are outside the cashier self-serve window and need a
 *  manager PIN — mirrors SaleService.CASHIER_SELF_SERVE_WINDOW on the backend. */
const SELF_SERVE_WINDOW_MS = 5 * 60 * 1000;

interface CorrectSalePickerModalProps {
  open: boolean;
  onClose: () => void;
  /** Called with the chosen sale — parent opens the correction modal for it. */
  onPick: (sale: SaleResponse) => void;
}

/**
 * Lists the sales rung up in the current open shift so the cashier can pick any
 * one to correct, not just the most recent. Sales within the self-serve window
 * are flagged as cashier-correctable; older ones note that a manager PIN will be
 * required (the correction modal still enforces this server-side).
 */
export function CorrectSalePickerModal({ open, onClose, onPick }: CorrectSalePickerModalProps) {
  const { data: sales = [], isLoading } = useQuery({
    queryKey: QK.currentSessionSales,
    queryFn: salesService.getCurrentSessionSales,
    enabled: open,
  });

  const fmt = (n: number | null | undefined) => `${CURRENCY.symbol} ${Number(n ?? 0).toFixed(2)}`;
  const timeOf = (iso: string) =>
    new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="dark sm:max-w-lg bg-card border-border text-foreground sm:rounded-2xl">
        <DialogHeader className="space-y-1">
          <DialogTitle className="text-lg font-bold text-foreground">Correct a sale</DialogTitle>
          <DialogDescription className="text-muted-foreground text-sm">
            Pick a sale from this shift. Recent sales you rang can be corrected directly;
            older ones need a manager PIN.
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[55vh] overflow-y-auto -mx-1 px-1 space-y-2 pt-1">
          {isLoading ? (
            <div className="flex items-center justify-center py-10 text-muted-foreground">
              <Loader2 className="animate-spin mr-2" size={18} /> Loading shift sales…
            </div>
          ) : sales.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-10 text-muted-foreground">
              <ReceiptIcon size={28} className="mb-2 opacity-50" aria-hidden="true" />
              <p className="text-sm">No sales in this shift yet.</p>
            </div>
          ) : (
            sales.map((sale) => {
              const isCash = sale.paymentMethod === 'CASH' || sale.paymentMethod === 'SPLIT';
              const age = Date.now() - new Date(sale.createdAt).getTime();
              const selfServe = age <= SELF_SERVE_WINDOW_MS;
              // Only PAID sales can be corrected; refunded/cancelled go through
              // Returns, so they're shown for context but not selectable.
              const correctable = sale.paymentStatus === 'PAID';
              const refunded = sale.paymentStatus === 'REFUNDED';
              return (
                <button
                  key={sale.id}
                  onClick={() => correctable && onPick(sale)}
                  disabled={!correctable}
                  title={correctable ? undefined : 'Only paid sales can be corrected — use Returns'}
                  className={cn(
                    'w-full text-left rounded-xl border p-3 transition-all',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                    correctable
                      ? 'bg-muted/40 border-border hover:bg-muted hover:border-primary/40'
                      : 'bg-muted/20 border-border/60 opacity-60 cursor-not-allowed',
                  )}
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-sm font-semibold text-foreground truncate">
                          {sale.invoiceNumber}
                        </span>
                        <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                          {sale.paymentMethod}
                        </span>
                        {refunded && (
                          <span className="text-[10px] font-bold uppercase tracking-wider text-warning">
                            Refunded
                          </span>
                        )}
                      </div>
                      <div className="text-xs text-muted-foreground mt-0.5 flex items-center gap-1">
                        <Clock size={11} aria-hidden="true" /> {timeOf(sale.createdAt)}
                        {isCash && sale.amountTendered != null && (
                          <span className="ml-2">
                            Tendered {fmt(sale.amountTendered)} · Change {fmt(sale.changeDue)}
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="text-right shrink-0">
                      <div className="font-bold tabular-nums text-foreground">{fmt(sale.netAmount)}</div>
                      <div
                        className={cn(
                          'text-[10px] font-semibold mt-0.5 inline-flex items-center gap-1',
                          selfServe ? 'text-success' : 'text-warning',
                        )}
                      >
                        {selfServe ? (
                          'No PIN'
                        ) : (
                          <>
                            <ShieldAlert size={11} aria-hidden="true" /> Manager PIN
                          </>
                        )}
                      </div>
                    </div>
                  </div>
                </button>
              );
            })
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
