'use client';

import { Package, ShoppingBag, LogOut, Store, Unlock, Square, ArrowLeft } from 'lucide-react';
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
    <div className="relative z-50 h-16 bg-gray-900/50 backdrop-blur-md border-b border-gray-800 flex items-center justify-between px-6">
      <div className="flex items-center gap-6">
        {onBackToDashboard && (
          <Button
            variant="outline"
            onClick={onBackToDashboard}
            className="bg-gray-950 border-gray-800 hover:bg-gray-800 text-gray-400 hover:text-primary gap-2 h-9 rounded-lg"
            title="Back to Dashboard"
          >
            <ArrowLeft size={16} /> Dashboard
          </Button>
        )}

        <div className="flex items-center gap-3">
          <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center">
            <Package className="text-white" size={18} />
          </div>
          <h1 className="text-xl font-bold tracking-tight">
            Store<span className="text-primary">X</span>
          </h1>
        </div>

        {/* Active branch — pinned to the open drawer for the shift, so it's read-only. */}
        {branchName && (
          <div className="border-l border-gray-800 pl-6 ml-2">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-gray-950 border border-gray-800">
              <Store size={14} className="text-primary" />
              <div className="flex flex-col items-start leading-none gap-0.5">
                <span className="text-[10px] text-gray-400 uppercase font-bold tracking-widest">Store Location</span>
                <span className="text-sm font-semibold text-gray-200">{branchName}</span>
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="flex items-center gap-4">
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
            className="hidden md:flex bg-gray-950 border-gray-800 hover:bg-gray-800 text-gray-400 hover:text-destructive gap-2 h-9 rounded-lg"
            title="End shift and count the cash drawer"
          >
            <Square size={16} /> End Shift
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
          className="hidden md:flex bg-gray-950 border-gray-800 hover:bg-gray-800 text-gray-400 hover:text-success gap-2 h-9 rounded-lg"
          title="Open Cash Drawer manually"
        >
          <Unlock size={16} /> Open Drawer
        </Button>
        
        <Button
          variant="outline"
          onClick={onShiftSummary}
          className="hidden lg:flex bg-gray-950 border-gray-800 hover:bg-gray-800 text-gray-400 gap-2 h-9 rounded-lg"
        >
          <ShoppingBag size={16} /> Shift Summary
        </Button>

        <div className="flex items-center gap-6 border-l border-gray-800 pl-4 ml-2">
          <div className="hidden md:flex flex-col items-end leading-tight">
            <span className="text-sm font-semibold text-white">{userName}</span>
            <span className="text-[10px] text-primary font-bold uppercase tracking-widest">
              {userRole}
            </span>
          </div>
          <Button variant="ghost" size="icon" onClick={onLogout} aria-label="Log out" title="Log out" className="text-gray-400 hover:text-destructive h-9 w-9 rounded-lg hover:bg-destructive/10 transition-all">
            <LogOut size={18} />
          </Button>
        </div>
      </div>
    </div>
  );
}
