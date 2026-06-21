'use client';

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';

const GROUPS: { title: string; rows: [string, string][] }[] = [
  {
    title: 'Navigate',
    rows: [
      ['↑ ↓ ← →', 'Move focus (grid / cart)'],
      ['Tab', 'Switch grid ↔ cart'],
      ['Enter', 'Add product / charge'],
      ['↓ in search', 'Jump into grid'],
    ],
  },
  {
    title: 'Cart line (focused)',
    rows: [
      ['+  /  −', 'Change quantity'],
      ['Del / ⌫', 'Remove line'],
      ['F6', 'Discount line'],
    ],
  },
  {
    title: 'Actions',
    rows: [
      ['F2', 'Search / scan'],
      ['F3', 'Add customer'],
      ['F4', 'Cycle payment'],
      ['F5', 'Hold sale'],
      ['F10', 'Add custom item'],
      ['F7', 'Correct a sale'],
      ['F8', 'Discard sale'],
      ['F9', 'Charge'],
      ['F12', 'Reprint receipt'],
    ],
  },
  {
    title: 'Payment (tender)',
    rows: [
      ['0–9 . ⌫', 'Enter amount'],
      ['← →', 'Payment method'],
      ['F10', 'Exact tender'],
      ['Enter', 'Complete sale'],
      ['Esc', 'Close / cancel'],
    ],
  },
];

/**
 * Keyboard-shortcuts reference, opened with F1 or "?". Portals to <body>, so it
 * carries its own `dark` class to match the force-dark terminal.
 */
export function ShortcutsOverlay({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="dark sm:max-w-3xl bg-card border-border text-foreground sm:rounded-2xl">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-foreground">Keyboard shortcuts</DialogTitle>
          <DialogDescription className="text-muted-foreground">
            Press F1 or ? anytime to open this. Esc to close.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-x-8 gap-y-5 sm:grid-cols-2 pt-1">
          {GROUPS.map((g) => (
            <div key={g.title}>
              <h3 className="text-[11px] font-bold uppercase tracking-wider text-primary mb-2">{g.title}</h3>
              <dl className="space-y-1.5">
                {g.rows.map(([k, label]) => (
                  <div key={k} className="flex items-center justify-between gap-3 text-sm">
                    <dt>
                      <kbd className="px-1.5 py-0.5 rounded bg-muted border border-border text-foreground font-mono text-xs whitespace-nowrap">
                        {k}
                      </kbd>
                    </dt>
                    <dd className="text-muted-foreground text-right flex-1">{label}</dd>
                  </div>
                ))}
              </dl>
            </div>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
