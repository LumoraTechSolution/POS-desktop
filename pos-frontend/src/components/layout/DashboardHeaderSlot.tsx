'use client';

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

type SlotContextValue = {
  set: (node: ReactNode) => void;
  clear: () => void;
};

const SlotContext = createContext<SlotContextValue | null>(null);

export function DashboardHeaderSlotProvider({
  children,
}: {
  children: (slot: ReactNode) => ReactNode;
}) {
  const [slot, setSlot] = useState<ReactNode>(null);

  const value: SlotContextValue = {
    set: (node) => setSlot(node),
    clear: () => setSlot(null),
  };

  return <SlotContext.Provider value={value}>{children(slot)}</SlotContext.Provider>;
}

/**
 * Renders its children into the dashboard header strip (breadcrumbs zone).
 * Pages use this to put breadcrumbs in the top header without prop-drilling.
 *
 * Usage:
 *   <DashboardHeaderSlot>
 *     <Breadcrumbs items={[{label: 'Customers', href: '/customers'}, {label: 'Jane Doe'}]} />
 *   </DashboardHeaderSlot>
 */
export function DashboardHeaderSlot({ children }: { children: ReactNode }) {
  const ctx = useContext(SlotContext);

  useEffect(() => {
    if (!ctx) return;
    ctx.set(children);
    return () => ctx.clear();
  }, [ctx, children]);

  return null;
}
