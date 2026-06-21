'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { useTheme } from 'next-themes';
import {
  LayoutDashboard,
  Package,
  Tags,
  Bookmark,
  Users,
  UserSquare2,
  BarChart3,
  Settings,
  Store,
  Building2,
  Truck,
  ArrowRightLeft,
  Monitor,
  LogOut,
  Sun,
  Moon,
  Search,
} from 'lucide-react';

import { Dialog, DialogContent } from '@/components/ui/dialog';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
  CommandShortcut,
} from '@/components/ui/command';
import { performLogout } from '@/lib/performLogout';

type Page = { label: string; href: string; icon: typeof Search; keywords?: string };
type Action = { label: string; icon: typeof Search; run: () => void; keywords?: string };

const PAGES: Page[] = [
  { label: 'Overview', href: '/overview', icon: LayoutDashboard },
  { label: 'Products', href: '/inventory/products', icon: Package, keywords: 'inventory' },
  { label: 'Categories', href: '/inventory/categories', icon: Tags },
  { label: 'Brands', href: '/inventory/brands', icon: Bookmark },
  { label: 'Customers', href: '/customers', icon: Users },
  { label: 'Suppliers', href: '/inventory/suppliers', icon: Building2 },
  { label: 'Purchase Orders', href: '/inventory/purchase-orders', icon: Truck, keywords: 'po' },
  { label: 'Stock Transfers', href: '/inventory/stock-transfers', icon: ArrowRightLeft },
  { label: 'Employees', href: '/employees', icon: UserSquare2 },
  { label: 'Reports', href: '/reports', icon: BarChart3 },
  { label: 'Branches', href: '/branches', icon: Store },
  { label: 'Settings', href: '/settings', icon: Settings },
  { label: 'POS Terminal', href: '/terminal', icon: Monitor, keywords: 'checkout cashier' },
];

type CommandPaletteProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Optional extra actions/pages contributed by the host (e.g. tenant-specific). */
  extraActions?: Action[];
  extraPages?: Page[];
};

export function CommandPalette({ open, onOpenChange, extraActions, extraPages }: CommandPaletteProps) {
  const router = useRouter();
  const { setTheme } = useTheme();

  const handleSelect = (run: () => void) => {
    onOpenChange(false);
    run();
  };

  const allPages = extraPages ? [...PAGES, ...extraPages] : PAGES;

  const actions: Action[] = [
    { label: 'Switch to light theme', icon: Sun, run: () => setTheme('light'), keywords: 'theme appearance' },
    { label: 'Switch to dark theme', icon: Moon, run: () => setTheme('dark'), keywords: 'theme appearance' },
    {
      label: 'Log out',
      icon: LogOut,
      run: async () => {
        await performLogout();
        router.push('/login');
      },
      keywords: 'signout exit',
    },
    ...(extraActions ?? []),
  ];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="p-0 overflow-hidden max-w-xl" hideCloseButton>
        <Command label="Command palette">
          <CommandInput placeholder="Search pages, actions, products…" />
          <CommandList>
            <CommandEmpty>No results found.</CommandEmpty>
            <CommandGroup heading="Pages">
              {allPages.map((page) => (
                <CommandItem
                  key={page.href}
                  value={`${page.label} ${page.keywords ?? ''} ${page.href}`}
                  onSelect={() => handleSelect(() => router.push(page.href))}
                >
                  <page.icon />
                  <span>{page.label}</span>
                </CommandItem>
              ))}
            </CommandGroup>
            <CommandSeparator />
            <CommandGroup heading="Actions">
              {actions.map((action) => (
                <CommandItem
                  key={action.label}
                  value={`${action.label} ${action.keywords ?? ''}`}
                  onSelect={() => handleSelect(action.run)}
                >
                  <action.icon />
                  <span>{action.label}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </DialogContent>
    </Dialog>
  );
}

/**
 * Convenience trigger button — shows the Cmd+K / Ctrl+K hint and binds the
 * hotkey itself. Drop this into a header alongside <CommandPalette/>.
 */
export function CommandPaletteTrigger({ children }: { children: (open: () => void) => ReactNode }) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'k' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <>
      {children(() => setOpen(true))}
      <CommandPalette open={open} onOpenChange={setOpen} />
    </>
  );
}

CommandPaletteTrigger.displayName = 'CommandPaletteTrigger';
CommandPalette.displayName = 'CommandPalette';

export { CommandShortcut };
