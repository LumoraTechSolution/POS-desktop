"use client";

import { useState, useEffect } from "react";
import { timeClockService } from "@/services/timeClockService";
import { cashSessionService } from "@/services/cashSessionService";
import { Button } from "@/components/ui/button";
import { Clock, Loader2, Play, Square } from "lucide-react";
import { toast } from "sonner";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { intervalToDuration } from "date-fns";
import { EndShiftModal } from "@/components/pos/EndShiftModal";
import { QK } from "@/lib/queryKeys";

interface TimeClockWidgetProps {
  variant?: 'sidebar' | 'header';
  /**
   * 'simple' uses the legacy clock-in/out flow (time record only).
   * 'cash-drawer' wires the button to the cash session lifecycle — Clock Out opens
   * the End Shift modal to reconcile the drawer. The Clock In button is hidden in
   * this mode because the page-level gate owns Start Shift.
   */
  shiftMode?: 'simple' | 'cash-drawer';
  /** Called after the End Shift modal successfully closes the drawer (cash-drawer
   *  mode). Lets the host (POS terminal) log the user out and return to login. */
  onShiftEnded?: () => void;
}

export function TimeClockWidget({ variant = 'sidebar', shiftMode = 'simple', onShiftEnded }: TimeClockWidgetProps) {
  const queryClient = useQueryClient();
  const [elapsed, setElapsed] = useState<string>("");
  const [endShiftOpen, setEndShiftOpen] = useState(false);

  // Fetch current status
  const { data: currentRecord, isLoading } = useQuery({
    queryKey: ["time-clock-status"],
    queryFn: () => timeClockService.getStatus(),
    refetchInterval: 60000, // Refresh every minute just in case
  });

  // In cash-drawer mode, mirror the active cash session so the widget reflects
  // reality if the session was opened from the page-level gate.
  const { data: activeSession } = useQuery({
    queryKey: QK.cashSessionActive,
    queryFn: () => cashSessionService.getActive(),
    enabled: shiftMode === 'cash-drawer',
    refetchInterval: 60000,
  });

  // Calculate ongoing elapsed time locally every second if clocked in
  useEffect(() => {
    let interval: NodeJS.Timeout;
    
    if (currentRecord?.clockInTime) {
      const startTime = new Date(currentRecord.clockInTime).getTime();
      
      const updateElapsed = () => {
        const now = new Date().getTime();
        const duration = intervalToDuration({ start: startTime, end: now });
        
        // Format as HH:MM:SS
        const hours = String(duration.hours || 0).padStart(2, '0');
        const minutes = String(duration.minutes || 0).padStart(2, '0');
        const seconds = String(duration.seconds || 0).padStart(2, '0');
        
        setElapsed(`${hours}:${minutes}:${seconds}`);
      };

      updateElapsed(); // initial call
      interval = setInterval(updateElapsed, 1000);
    } else {
      setElapsed("");
    }

    return () => clearInterval(interval);
  }, [currentRecord?.clockInTime]);

  // Clock In Mutation
  const clockInMutation = useMutation({
    mutationFn: () => timeClockService.clockIn(),
    onSuccess: () => {
      toast.success("Clocked in successfully");
      queryClient.invalidateQueries({ queryKey: ["time-clock-status"] });
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to clock in");
    }
  });

  // Clock Out Mutation
  const clockOutMutation = useMutation({
    mutationFn: () => timeClockService.clockOut(),
    onSuccess: (data) => {
      toast.success(`Clocked out. Total duration: ${data.durationMinutes}m`);
      queryClient.invalidateQueries({ queryKey: ["time-clock-status"] });
    },
    onError: (error: unknown) => {
      toast.error((error as { response?: { data?: { message?: string } } })?.response?.data?.message || "Failed to clock out");
    }
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-3 bg-card border border-border rounded-xl">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const isClockedIn = !!currentRecord;
  const isCashDrawerMode = shiftMode === 'cash-drawer';
  const hasOpenSession = !!activeSession;

  const handleClockInClick = () => {
    if (isCashDrawerMode) {
      // In cash-drawer mode the page-level gate owns start-shift; nothing to do here.
      return;
    }
    clockInMutation.mutate();
  };

  const handleClockOutClick = () => {
    if (isCashDrawerMode && hasOpenSession) {
      setEndShiftOpen(true);
    } else {
      clockOutMutation.mutate();
    }
  };

  // In cash-drawer mode without an open session, don't render a Clock In button —
  // the terminal page shows the Start Shift gate instead. Render a compact placeholder.
  if (isCashDrawerMode && !hasOpenSession) {
    return variant === 'header' ? (
      <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg border bg-background border-border">
        <Clock size={16} className="text-muted-foreground" />
        <span className="text-xs text-muted-foreground">Shift not started</span>
      </div>
    ) : (
      <div className="flex items-center gap-2 p-3 bg-card/50 border border-border rounded-xl">
        <Clock size={16} className="text-muted-foreground" />
        <span className="text-sm text-muted-foreground">Shift not started</span>
      </div>
    );
  }

  if (variant === 'header') {
    return (
      <>
        <div className={`flex items-center gap-3 px-3 py-1.5 rounded-lg border transition-colors ${isClockedIn ? 'bg-emerald-950/30 border-emerald-900/50' : 'bg-background border-border'}`}>
          <div className="flex items-center gap-2">
            <Clock size={16} className={isClockedIn ? "text-success" : "text-muted-foreground"} />
            {isClockedIn && (
              <span className="text-xs font-mono font-medium text-success w-[60px] text-center">
                {elapsed}
              </span>
            )}
          </div>
          <div className="w-px h-4 bg-muted mx-1" />
          {isClockedIn ? (
            <Button
              variant="ghost"
              size="sm"
              className="h-7 px-2 text-xs font-semibold text-destructive hover:text-red-300 hover:bg-destructive/10"
              onClick={handleClockOutClick}
              disabled={clockOutMutation.isPending}
            >
              {clockOutMutation.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : (isCashDrawerMode ? 'End Shift' : 'Clock Out')}
            </Button>
          ) : (
            <Button
              variant="ghost"
              size="sm"
              className="h-7 px-2 text-xs font-semibold text-success hover:text-emerald-300 hover:bg-success/10"
              onClick={handleClockInClick}
              disabled={clockInMutation.isPending}
            >
              {clockInMutation.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : 'Clock In'}
            </Button>
          )}
        </div>
        {isCashDrawerMode && (
          <EndShiftModal
            open={endShiftOpen}
            onClose={() => setEndShiftOpen(false)}
            onEnded={onShiftEnded}
          />
        )}
      </>
    );
  }

  return (
    <>
      <div className={`flex flex-col gap-2 p-3 ${isClockedIn ? 'bg-emerald-950/20 border-emerald-900/40' : 'bg-card/50 border-border'} border rounded-xl transition-colors`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Clock size={16} className={isClockedIn ? "text-success" : "text-muted-foreground"} />
            <span className="text-sm font-medium text-foreground">
              {isClockedIn ? "On the clock" : "Off the clock"}
            </span>
          </div>

          {isClockedIn && (
            <span className="text-xs font-mono text-success bg-emerald-950/50 px-2 py-0.5 rounded border border-emerald-900/50">
              {elapsed}
            </span>
          )}
        </div>

        {isClockedIn ? (
          <Button
            variant="destructive"
            size="sm"
            className="w-full bg-destructive/20 text-destructive hover:bg-destructive/40 border border-destructive/30"
            onClick={handleClockOutClick}
            disabled={clockOutMutation.isPending}
          >
            {clockOutMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Square className="mr-2 h-4 w-4" />}
            {isCashDrawerMode ? 'End Shift' : 'Clock Out'}
          </Button>
        ) : (
          <Button
            variant="default"
            size="sm"
            className="w-full bg-success/20 text-success hover:bg-success/40 border border-success/30"
            onClick={handleClockInClick}
            disabled={clockInMutation.isPending}
          >
            {clockInMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Play className="mr-2 h-4 w-4" />}
            Clock In
          </Button>
        )}
      </div>
      {isCashDrawerMode && (
        <EndShiftModal open={endShiftOpen} onClose={() => setEndShiftOpen(false)} />
      )}
    </>
  );
}
