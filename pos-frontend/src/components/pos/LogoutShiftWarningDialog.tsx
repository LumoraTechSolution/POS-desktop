'use client';

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { AlertTriangle, Square, LogOut } from 'lucide-react';
import { fc } from '@/lib/utils';

interface LogoutShiftWarningDialogProps {
  open: boolean;
  /** System-expected cash in the drawer (opening + cash sales − cash refunds). */
  expectedAmount: number;
  onCancel: () => void;
  /** Jump to the End Shift / drawer-count flow instead of logging out. */
  onEndShift: () => void;
  /** Log out without counting — the drawer is auto-closed server-side, unreconciled. */
  onLogoutAnyway: () => void;
}

/**
 * Shown when a user with an OPEN cash drawer tries to log out. Rather than
 * silently auto-closing the shift, we warn and offer to count the drawer first.
 * "Log out anyway" still proceeds — the backend auto-closes the session — but
 * the choice is now explicit, not silent.
 */
export function LogoutShiftWarningDialog({
  open,
  expectedAmount,
  onCancel,
  onEndShift,
  onLogoutAnyway,
}: LogoutShiftWarningDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onCancel()}>
      <DialogContent className="max-w-md sm:rounded-2xl bg-gray-900 border-gray-800 text-white">
        <DialogHeader>
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-warning/10 text-warning">
              <AlertTriangle className="h-5 w-5" />
            </div>
            <div className="flex-1 space-y-1.5">
              <DialogTitle className="text-base font-semibold">
                Your cash drawer is still open
              </DialogTitle>
              <DialogDescription className="text-sm text-gray-400">
                You have an open shift with{' '}
                <span className="font-semibold text-gray-200">{fc(expectedAmount)}</span>{' '}
                expected in the drawer. Count it down to reconcile before you leave. If you log
                out anyway, the shift is closed automatically without a count and no variance is
                recorded.
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="mt-4 flex flex-col gap-2">
          {/* Primary, recommended action first and full-width for emphasis. */}
          <Button
            type="button"
            onClick={onEndShift}
            className="w-full justify-center bg-primary hover:bg-primary/90 text-primary-foreground gap-2"
          >
            <Square size={16} /> End shift &amp; count
          </Button>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={onCancel}
              className="flex-1 justify-center border-gray-700 hover:bg-gray-800"
            >
              Cancel
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={onLogoutAnyway}
              className="flex-1 justify-center border-gray-700 text-destructive hover:bg-destructive/10 hover:text-destructive gap-2"
            >
              <LogOut size={16} /> Log out anyway
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
