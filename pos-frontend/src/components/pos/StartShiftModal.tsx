"use client";

import { useState, useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cashSessionService, CashSession } from "@/services/cashSessionService";
import { branchService } from "@/services/branchService";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { toast } from "sonner";
import { Wallet, Loader2 } from "lucide-react";
import { QK } from "@/lib/queryKeys";

interface StartShiftModalProps {
  open: boolean;
  /** Triggered when the user clicks Cancel. Usually navigates away from the terminal. */
  onCancel: () => void;
  onStarted?: (session: CashSession) => void;
}

export function StartShiftModal({ open, onCancel, onStarted }: StartShiftModalProps) {
  const queryClient = useQueryClient();
  const [openingBalance, setOpeningBalance] = useState<string>("");
  const [notes, setNotes] = useState("");
  const [branchId, setBranchId] = useState<string>("");

  // Branches this user may open a drawer at. With one branch the picker is hidden
  // and that branch is used automatically; with several the user chooses where.
  const { data: myBranches } = useQuery({
    queryKey: ["branches", "me"],
    queryFn: () => branchService.getMyBranches(),
  });

  useEffect(() => {
    if (myBranches && myBranches.length > 0 && !branchId) {
      setBranchId((myBranches.find(b => b.isDefault) || myBranches[0]).id);
    }
  }, [myBranches, branchId]);

  const multiBranch = (myBranches?.length ?? 0) > 1;

  const startMutation = useMutation({
    mutationFn: () => cashSessionService.start(Number(openingBalance), branchId || undefined, notes || undefined),
    onSuccess: (session) => {
      toast.success("Shift started. Good luck out there.");
      queryClient.invalidateQueries({ queryKey: QK.cashSessionActive });
      queryClient.invalidateQueries({ queryKey: ["time-clock-status"] });
      onStarted?.(session);
    },
    onError: (error: unknown) => {
      toast.error(
        (error as { response?: { data?: { message?: string } } })?.response?.data?.message ||
          "Failed to start shift"
      );
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const amount = Number(openingBalance);
    if (!Number.isFinite(amount) || amount < 0) {
      toast.error("Enter a valid opening cash amount");
      return;
    }
    startMutation.mutate();
  };

  return (
    <Dialog open={open}>
      <DialogContent
        className="sm:max-w-[460px] bg-gray-900 border-gray-800 text-white"
        onInteractOutside={(e) => e.preventDefault()}
        onEscapeKeyDown={(e) => e.preventDefault()}
        hideCloseButton
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Wallet className="h-5 w-5 text-primary" />
            Start your shift
          </DialogTitle>
          <DialogDescription className="text-gray-400">
            Count the cash in the drawer and enter the starting amount. You&apos;ll reconcile this
            at clock-out.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4 pt-2">
          {multiBranch && (
            <div className="space-y-2">
              <Label htmlFor="shiftBranch">Branch *</Label>
              <select
                id="shiftBranch"
                value={branchId}
                onChange={(e) => setBranchId(e.target.value)}
                className="w-full rounded-md bg-gray-950 border border-gray-800 px-3 py-2 text-sm focus-visible:ring-primary focus-visible:outline-none"
              >
                {(myBranches ?? []).map((b) => (
                  <option key={b.id} value={b.id}>{b.name}</option>
                ))}
              </select>
              <p className="text-xs text-gray-500">Your drawer is tied to this branch for the whole shift.</p>
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="openingBalance">Opening cash in drawer *</Label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500 text-sm">$</span>
              <Input
                id="openingBalance"
                type="number"
                step="0.01"
                min="0"
                required
                autoFocus
                value={openingBalance}
                onChange={(e) => setOpeningBalance(e.target.value)}
                className="pl-7 bg-gray-950 border-gray-800 focus-visible:ring-primary text-lg font-mono"
                placeholder="0.00"
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="startNotes">Notes (optional)</Label>
            <Textarea
              id="startNotes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={2}
              className="bg-gray-950 border-gray-800 focus-visible:ring-primary"
              placeholder="Anything worth remembering about the opening count"
            />
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={onCancel}
              disabled={startMutation.isPending}
              className="border-gray-700 bg-transparent text-gray-200 hover:bg-gray-800 hover:text-white"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={startMutation.isPending}
              className="bg-primary hover:bg-primary/90 text-primary-foreground min-w-[120px]"
            >
              {startMutation.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                "Start Shift"
              )}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
