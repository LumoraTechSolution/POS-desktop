'use client';

import React, { useCallback, useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { AlertTriangle, ShieldCheck } from 'lucide-react';

export type ConfirmVariant = 'default' | 'destructive';

export interface ConfirmDialogOptions {
  title: string;
  description: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: ConfirmVariant;
}

interface PendingConfirm extends ConfirmDialogOptions {
  resolve: (value: boolean) => void;
}

/**
 * Promise-based confirmation dialog. Drop-in replacement for window.confirm()
 * with shadcn Dialog styling. Use in any component:
 *
 *   const { confirm, dialog } = useConfirmDialog();
 *   ...
 *   const ok = await confirm({ title, description, variant: 'destructive' });
 *   if (!ok) return;
 *   ...
 *   return <>{dialog}{otherContent}</>;
 */
export function useConfirmDialog() {
  const [pending, setPending] = useState<PendingConfirm | null>(null);

  const confirm = useCallback(
    (options: ConfirmDialogOptions) =>
      new Promise<boolean>((resolve) => {
        setPending({ ...options, resolve });
      }),
    []
  );

  const settle = (value: boolean) => {
    pending?.resolve(value);
    setPending(null);
  };

  const variant: ConfirmVariant = pending?.variant ?? 'default';
  const isDestructive = variant === 'destructive';

  const dialog = (
    <Dialog
      open={pending !== null}
      onOpenChange={(open) => {
        if (!open) settle(false);
      }}
    >
      <DialogContent
        className="max-w-md sm:rounded-2xl"
        onEscapeKeyDown={() => settle(false)}
      >
        <DialogHeader>
          <div className="flex items-start gap-3">
            <div
              className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${
                isDestructive
                  ? 'bg-destructive/10 text-destructive'
                  : 'bg-primary/10 text-primary'
              }`}
            >
              {isDestructive ? (
                <AlertTriangle className="h-5 w-5" />
              ) : (
                <ShieldCheck className="h-5 w-5" />
              )}
            </div>
            <div className="flex-1 space-y-1.5">
              <DialogTitle className="text-base font-semibold text-foreground">
                {pending?.title}
              </DialogTitle>
              <DialogDescription className="text-sm text-muted-foreground">
                {pending?.description}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <DialogFooter className="mt-2 gap-2 sm:gap-2">
          <button
            type="button"
            onClick={() => settle(false)}
            className="rounded-lg border border-border bg-background px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            {pending?.cancelLabel ?? 'Cancel'}
          </button>
          <button
            type="button"
            onClick={() => settle(true)}
            className={`rounded-lg px-4 py-2 text-sm font-semibold shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${
              isDestructive
                ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90'
                : 'bg-primary text-primary-foreground hover:bg-primary/90'
            }`}
          >
            {pending?.confirmLabel ?? 'Confirm'}
          </button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );

  return { confirm, dialog };
}
