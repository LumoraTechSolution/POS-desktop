'use client';

import { useEffect, useState } from 'react';
import { useTheme } from 'next-themes';
import { Sun, Moon, Monitor } from 'lucide-react';
import { Button } from '@/components/ui/button';

const ORDER = ['light', 'dark', 'system'] as const;
type ThemeKey = (typeof ORDER)[number];

const META: Record<ThemeKey, { icon: typeof Sun; label: string; next: ThemeKey }> = {
  light: { icon: Sun, label: 'Light', next: 'dark' },
  dark: { icon: Moon, label: 'Dark', next: 'system' },
  system: { icon: Monitor, label: 'System', next: 'light' },
};

export function ThemeToggle({ className }: { className?: string }) {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  useEffect(() => setMounted(true), []);

  const current: ThemeKey =
    !mounted || !theme || !ORDER.includes(theme as ThemeKey) ? 'dark' : (theme as ThemeKey);
  const { icon: Icon, label, next } = META[current];

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={() => setTheme(next)}
      aria-label={`Theme: ${label}. Click to switch to ${META[next].label}.`}
      title={`Theme: ${label} (click for ${META[next].label})`}
      className={className}
    >
      <Icon size={18} />
    </Button>
  );
}
