'use client';

import { ShoppingBag, LogOut, Store, Unlock, Square, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { TimeClockWidget } from '@/components/employee/TimeClockWidget';
import { FeatureGuard } from '@/components/auth/FeatureGuard';

interface POSHeaderProps {
  userName: string;
  userRole: string;
  /** Branch this terminal is operating at. Pinned to the open drawer's branch for the
   *  whole shift, so it's shown as a static label rather than a switcher. */
  branchName: string | null;
  onShiftSummary: () => void;
  /** Opens the End Shift / drawer-count flow. Admins reach it from here since
   *  the TimeClockWidget (which owns End Shift for staff) is hidden for them. */
  onEndShift: () => void;
  /** Fired after a shift is successfully ended (from staff's TimeClockWidget) so
   *  the page can log the user out and return to login. */
  onShiftEnded: () => void;
  onLogout: () => void;
  /** Return to the management dashboard. Only supplied for full email/password
   *  sessions — a PIN (at-the-register) login has no dashboard access, so the
   *  terminal passes this undefined and the button is hidden. */
  onBackToDashboard?: () => void;
}

export function POSHeader({
  userName,
  userRole,
  branchName,
  onShiftSummary,
  onEndShift,
  onShiftEnded,
  onLogout,
  onBackToDashboard
}: POSHeaderProps) {
  return (
    // This bar only spans the product column (the cart panel sits to its right),
    // so its usable width is much smaller than the window. Tailwind breakpoints
    // are window-based, so secondary controls are collapsed/hidden generously and
    // the user + logout cluster is shrink-0 so it can never be clipped.
    <div className="relative z-50 h-16 bg-gray-900/50 backdrop-blur-md border-b border-gray-800 flex items-center justify-between gap-2 px-4">
      <div className="flex items-center gap-3 min-w-0">
        {onBackToDashboard && (
          <Button
            variant="outline"
            onClick={onBackToDashboard}
            className="shrink-0 bg-gray-950 border-gray-800 hover:bg-gray-800 text-gray-400 hover:text-primary gap-2 h-9 px-2.5 lg:px-4 rounded-lg"
            title="Back to Dashboard"
          >
            <ArrowLeft size={16} /> <span className="hidden lg:inline">Dashboard</span>
          </Button>
        )}

        <h1 className="text-xl font-bold tracking-tight shrink-0">
          Store<span className="text-primary">X</span>
        </h1>

        {/* Active branch — pinned to the open drawer for the shift, so it's read-only.
            Hidden on narrow widths; the branch name truncates rather than pushing
            the rest of the bar off-screen. */}
        {branchName && (
          <div className="hidden xl:flex border-l border-gray-800 pl-4 ml-1 min-w-0">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-gray-950 border border-gray-800 min-w-0">
              <Store size={14} className="text-primary shrink-0" />
              <div className="flex flex-col items-start leading-none gap-0.5 min-w-0">
                <span className="text-[10px] text-gray-400 uppercase font-bold tracking-widest">Store Location</span>
                <span className="text-sm font-semibold text-gray-200 truncate max-w-[160px]">{branchName}</span>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="flex items-center gap-2 shrink-0">
        {userRole !== 'ADMIN' && (
          <FeatureGuard feature="TIME_CLOCK">
            <TimeClockWidget variant="header" shiftMode="cash-drawer" onShiftEnded={onShiftEnded} />
          </FeatureGuard>
        )}

        {/* Admins have no TimeClockWidget, so they get a dedicated End Shift
            control to count down and close the cash drawer they opened. */}
        {userRole === 'ADMIN' && (
          <Button
            variant="outline"
            onClick={onEndShift}
            className="hidden lg:flex bg-gray-950 border-gray-800 hover:bg-gray-800 text-gray-400 hover:text-destructive gap-2 h-9 px-2.5 2xl:px-4 rounded-lg"
            title="End shift and count the cash drawer"
          >
            <Square size={16} /> <span className="hidden 2xl:inline">End Shift</span>
          </Button>
        )}

        <Button
          variant="outline"
          onClick={async () => {
            try {
              const { default: api } = await import('@/services/api');
              await api.post('/terminal/hardware/open-drawer');
              const { hardwareService } = await import('@/services/hardwareService');
              hardwareService.kickCashDrawer();
            } catch (err) {
              console.error("Failed to audit drawer opening", err);
              // Do NOT open the drawer if the audit fails
            }
          }}
          className="hidden lg:flex bg-gray-950 border-gray-800 hover:bg-gray-800 text-gray-400 hover:text-success gap-2 h-9 px-2.5 2xl:px-4 rounded-lg"
          title="Open Cash Drawer manually"
        >
          <Unlock size={16} /> <span className="hidden 2xl:inline">Open Drawer</span>
        </Button>

        <Button
          variant="outline"
          onClick={onShiftSummary}
          className="hidden xl:flex bg-gray-950 border-gray-800 hover:bg-gray-800 text-gray-400 gap-2 h-9 px-2.5 2xl:px-4 rounded-lg"
          title="Shift Summary"
        >
          <ShoppingBag size={16} /> <span className="hidden 2xl:inline">Shift Summary</span>
        </Button>

        <div className="flex items-center gap-3 border-l border-gray-800 pl-3 ml-1 shrink-0">
          <div className="hidden sm:flex flex-col items-end leading-tight min-w-0">
            <span className="text-sm font-semibold text-white truncate max-w-[140px]">{userName}</span>
            <span className="text-[10px] text-primary font-bold uppercase tracking-widest">
              {userRole}
            </span>
          </div>
          <Button variant="ghost" size="icon" onClick={onLogout} aria-label="Log out" title="Log out" className="shrink-0 text-gray-400 hover:text-destructive h-9 w-9 rounded-lg hover:bg-destructive/10 transition-all">
            <LogOut size={18} />
          </Button>
        </div>
      </div>
    </div>
  );
}
