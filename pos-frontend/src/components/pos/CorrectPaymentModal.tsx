'use client';

import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Banknote, CreditCard, QrCode, SplitSquareHorizontal, ArrowRightLeft, Loader2, Delete } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { NumberPad } from '@/components/ui/number-pad';
import { CURRENCY, cn } from '@/lib/utils';
import { toast } from 'sonner';
import { PaymentCorrectionRequest, SaleResponse, salesService } from '@/services/salesService';

type CorrectionMethod = 'CASH' | 'CARD' | 'ONLINE' | 'SPLIT' | 'CREDIT';

const METHOD_OPTIONS: { method: CorrectionMethod; icon: typeof Banknote; label: string }[] = [
  { method: 'CASH', icon: Banknote, label: 'Cash' },
  { method: 'CARD', icon: CreditCard, label: 'Card' },
  { method: 'ONLINE', icon: QrCode, label: 'Online' },
  { method: 'SPLIT', icon: SplitSquareHorizontal, label: 'Split' },
];

type Step = 'choose' | 'method' | 'tender' | 'pin';

interface CorrectPaymentModalProps {
  open: boolean;
  sale: SaleResponse | null;
  onClose: () => void;
  /** Called after a successful correction with the updated sale. */
  onCorrected: (updated: SaleResponse) => void;
  /** Called when the cashier chose "Item error → Refund" — parent opens the Return modal. */
  onRequestReturn: (sale: SaleResponse) => void;
}

/**
 * Post-completion payment corrections for the cashier:
 *  - change the recorded payment method, OR
 *  - change the recorded cash tendered.
 * Item / quantity errors are forwarded to the existing Return flow.
 *
 * Authorization is enforced server-side: the modal first tries the call
 * without a PIN. If the backend responds "Manager PIN is required" we drop
 * into the PIN step and retry. This keeps us from having to leak the
 * cashier's UUID through the SaleResponse just to decide who owns the sale.
 */
export function CorrectPaymentModal({
  open,
  sale,
  onClose,
  onCorrected,
  onRequestReturn,
}: CorrectPaymentModalProps) {
  const [step, setStep] = useState<Step>('choose');
  const [pendingMethod, setPendingMethod] = useState<CorrectionMethod | null>(null);
  const [tenderStr, setTenderStr] = useState('');
  const [pin, setPin] = useState('');
  /** When set, submitting the PIN should resend this payload to the backend. */
  const [retryPayload, setRetryPayload] = useState<PaymentCorrectionRequest | null>(null);

  useEffect(() => {
    if (!open) return;
    setStep('choose');
    setPendingMethod(null);
    setTenderStr('');
    setPin('');
    setRetryPayload(null);
  }, [open, sale?.id]);

  const correctMutation = useMutation({
    mutationFn: ({ saleId, data }: { saleId: string; data: PaymentCorrectionRequest }) =>
      salesService.correctPayment(saleId, data),
    onSuccess: (updated) => {
      toast.success('Payment corrected');
      onCorrected(updated);
      onClose();
    },
    onError: (error: unknown) => {
      const message =
        (error as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to correct payment';
      if (/manager pin is required/i.test(message)) {
        setStep('pin');
        return;
      }
      if (/invalid manager pin/i.test(message)) {
        toast.error('Invalid manager PIN');
        setPin('');
        return;
      }
      toast.error(message);
    },
  });

  const submit = (payload: PaymentCorrectionRequest) => {
    if (!sale) return;
    setRetryPayload(payload);
    correctMutation.mutate({ saleId: sale.id, data: payload });
  };

  const submitWithPin = () => {
    if (!retryPayload || !sale) return;
    if (pin.length !== 4) {
      toast.error('Enter the 4-digit manager PIN');
      return;
    }
    correctMutation.mutate({
      saleId: sale.id,
      data: { ...retryPayload, managerPin: pin },
    });
  };

  if (!sale) return null;

  const net = Number(sale.netAmount ?? 0);

  // The method that will be in effect after the correction: a pending method
  // change if the cashier picked one, otherwise the sale's current method.
  const effectiveMethod: CorrectionMethod = pendingMethod ?? (sale.paymentMethod as CorrectionMethod);
  const isSplitTender = effectiveMethod === 'SPLIT';
  const tenderNum = parseFloat(tenderStr) || 0;
  // CASH: customer must hand over at least the net (change given back).
  // SPLIT: the cash portion is bounded to [0, net].
  const tenderValid = isSplitTender ? tenderNum >= 0 && tenderNum <= net : tenderNum >= net;
  const changePreview = isSplitTender ? 0 : Math.max(0, tenderNum - net);

  /** Submit the tender step, carrying a method change too when we routed here
   *  from "Change payment method" → CASH/SPLIT. */
  const submitTender = () => {
    const payload: PaymentCorrectionRequest = { cashTendered: tenderNum };
    if (pendingMethod) payload.paymentMethod = pendingMethod;
    submit(payload);
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="dark sm:max-w-lg bg-card border-border text-foreground sm:rounded-2xl">
        <DialogHeader className="space-y-1">
          <DialogTitle className="text-lg font-bold text-foreground">
            Correct payment{sale.invoiceNumber ? ` — ${sale.invoiceNumber}` : ''}
          </DialogTitle>
          <DialogDescription className="text-muted-foreground text-sm">
            Recorded as <span className="font-semibold text-foreground">{sale.paymentMethod}</span>
            {' '}for{' '}<span className="font-semibold text-foreground tabular-nums">
              {CURRENCY.symbol} {net.toFixed(2)}
            </span>.
          </DialogDescription>
        </DialogHeader>

        {step === 'choose' && (
          <div className="space-y-2 pt-1">
            <Button
              variant="outline"
              className="w-full justify-start h-14 text-base rounded-xl"
              onClick={() => setStep('method')}
            >
              <CreditCard className="mr-3" size={20} aria-hidden="true" />
              Change payment method
            </Button>
            {/* Only meaningful when cash physically changed hands. For a CARD/
                ONLINE sale, switch the method first (which captures the cash). */}
            {(sale.paymentMethod === 'CASH' || sale.paymentMethod === 'SPLIT') && (
              <Button
                variant="outline"
                className="w-full justify-start h-14 text-base rounded-xl"
                onClick={() => {
                  setPendingMethod(null);
                  setTenderStr('');
                  setStep('tender');
                }}
              >
                <Banknote className="mr-3" size={20} aria-hidden="true" />
                Change cash tendered
              </Button>
            )}
            <Button
              variant="outline"
              className="w-full justify-start h-14 text-base rounded-xl"
              onClick={() => {
                onClose();
                onRequestReturn(sale);
              }}
            >
              <ArrowRightLeft className="mr-3" size={20} aria-hidden="true" />
              Wrong item or quantity — refund
            </Button>
          </div>
        )}

        {step === 'method' && (
          <div className="space-y-4 pt-1">
            <div className="grid grid-cols-4 gap-2" role="radiogroup" aria-label="New payment method">
              {METHOD_OPTIONS.map(({ method, icon: Icon, label }) => {
                const isSelected = pendingMethod === method;
                return (
                  <button
                    key={method}
                    role="radio"
                    aria-checked={isSelected}
                    onClick={() => setPendingMethod(method)}
                    className={cn(
                      'flex flex-col items-center justify-center min-h-touch py-3 rounded-xl border transition-all',
                      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                      isSelected
                        ? 'bg-primary border-primary text-primary-foreground shadow-lg shadow-primary/20'
                        : 'bg-muted/40 border-border text-muted-foreground hover:bg-muted hover:text-foreground'
                    )}
                  >
                    <Icon size={22} className="mb-1" aria-hidden="true" />
                    <span className="text-[11px] font-bold uppercase tracking-wider">{label}</span>
                  </button>
                );
              })}
            </div>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setStep('choose')} disabled={correctMutation.isPending}>
                Back
              </Button>
              <Button
                className="flex-1"
                disabled={!pendingMethod || pendingMethod === sale.paymentMethod || correctMutation.isPending}
                onClick={() => {
                  if (!pendingMethod) return;
                  // Switching to a method that takes cash → capture how much the
                  // customer actually paid before applying, so the receipt and
                  // drawer reflect reality instead of an assumed exact tender.
                  if (pendingMethod === 'CASH' || pendingMethod === 'SPLIT') {
                    setTenderStr('');
                    setStep('tender');
                  } else {
                    submit({ paymentMethod: pendingMethod });
                  }
                }}
              >
                {correctMutation.isPending
                  ? <Loader2 className="animate-spin" size={18} />
                  : pendingMethod === 'CASH' || pendingMethod === 'SPLIT' ? 'Next' : 'Apply'}
              </Button>
            </div>
          </div>
        )}

        {step === 'tender' && (
          <div className="space-y-4 pt-1">
            <div className="flex items-baseline justify-between">
              <label className="text-xs text-muted-foreground font-medium uppercase tracking-wider">
                {pendingMethod
                  ? `Change to ${effectiveMethod} — ${isSplitTender ? 'cash portion' : 'amount paid'}`
                  : isSplitTender ? 'New cash portion' : 'Amount customer paid'}
              </label>
              <div className="text-xl font-bold text-foreground tabular-nums">
                {CURRENCY.symbol} {tenderNum.toFixed(2)}
              </div>
            </div>

            {/* Net + live change so the cashier confirms what's owed back. */}
            <div className="flex items-center justify-between text-sm rounded-lg bg-muted/40 border border-border px-3 py-2">
              <span className="text-muted-foreground">Total due</span>
              <span className="font-semibold text-foreground tabular-nums">
                {CURRENCY.symbol} {net.toFixed(2)}
              </span>
            </div>
            {!isSplitTender && (
              <div className="flex items-center justify-between text-sm px-3">
                <span className="text-muted-foreground">Change to return</span>
                <span className="font-semibold text-foreground tabular-nums">
                  {CURRENCY.symbol} {changePreview.toFixed(2)}
                </span>
              </div>
            )}

            <NumberPad value={tenderStr} onChange={setTenderStr} />

            {tenderStr && !tenderValid && (
              <p className="text-xs text-destructive text-center">
                {isSplitTender
                  ? `Cash portion must be between ${CURRENCY.symbol} 0.00 and ${CURRENCY.symbol} ${net.toFixed(2)}`
                  : `Amount paid can't be less than the ${CURRENCY.symbol} ${net.toFixed(2)} due`}
              </p>
            )}

            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={() => setStep(pendingMethod ? 'method' : 'choose')}
                disabled={correctMutation.isPending}
              >
                Back
              </Button>
              <Button
                className="flex-1"
                disabled={!tenderStr || !tenderValid || correctMutation.isPending}
                onClick={submitTender}
              >
                {correctMutation.isPending ? <Loader2 className="animate-spin" size={18} /> : 'Apply'}
              </Button>
            </div>
          </div>
        )}

        {step === 'pin' && (
          <div className="space-y-4 pt-1">
            <p className="text-sm text-muted-foreground">
              This correction needs a manager PIN.
            </p>
            <div className="flex justify-center gap-3" aria-label="PIN entry">
              {[1, 2, 3, 4].map((i) => (
                <div
                  key={i}
                  className={cn(
                    'w-4 h-4 rounded-full border-2 border-primary',
                    pin.length >= i ? 'bg-primary' : 'bg-transparent'
                  )}
                />
              ))}
            </div>
            <div className="grid grid-cols-3 gap-2">
              {[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => (
                <Button
                  key={n}
                  variant="outline"
                  size="touch"
                  className="text-lg font-semibold"
                  onClick={() => setPin((p) => (p.length < 4 ? p + n : p))}
                  disabled={correctMutation.isPending}
                >
                  {n}
                </Button>
              ))}
              <Button
                variant="outline"
                size="touch-icon"
                aria-label="Backspace"
                onClick={() => setPin((p) => p.slice(0, -1))}
                disabled={correctMutation.isPending || pin.length === 0}
              >
                <Delete />
              </Button>
              <Button
                variant="outline"
                size="touch"
                className="text-lg font-semibold"
                onClick={() => setPin((p) => (p.length < 4 ? p + '0' : p))}
                disabled={correctMutation.isPending}
              >
                0
              </Button>
              <Button
                variant="default"
                size="touch"
                className="font-bold"
                onClick={submitWithPin}
                disabled={correctMutation.isPending || pin.length !== 4}
              >
                {correctMutation.isPending ? <Loader2 className="animate-spin" size={18} /> : 'OK'}
              </Button>
            </div>
            <Button
              variant="ghost"
              className="w-full"
              onClick={() => setStep('choose')}
              disabled={correctMutation.isPending}
            >
              Cancel
            </Button>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
