import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import PlanBadge from './PlanBadge';

describe('<PlanBadge />', () => {
  it('renders the human label for SMALL_BUSINESS in blue', () => {
    render(<PlanBadge plan="SMALL_BUSINESS" />);
    const el = screen.getByText('Small Business');
    expect(el).toBeInTheDocument();
    expect(el.className).toMatch(/bg-blue-50/);
    expect(el.className).toMatch(/text-blue-700/);
  });

  it('renders MEDIUM_BUSINESS with the purple palette', () => {
    render(<PlanBadge plan="MEDIUM_BUSINESS" />);
    const el = screen.getByText('Medium Business');
    expect(el.className).toMatch(/bg-purple-50/);
  });

  it('renders ENTERPRISE with the amber palette and an icon', () => {
    const { container } = render(<PlanBadge plan="ENTERPRISE" />);
    const el = screen.getByText('Enterprise');
    expect(el.className).toMatch(/bg-amber-50/);
    // The lucide ShieldAlert icon renders as an inline <svg>.
    expect(container.querySelector('svg')).not.toBeNull();
  });

  it('falls back to a humanised label and gray palette for unknown tiers', () => {
    render(<PlanBadge plan="FREE_TRIAL" />);
    const el = screen.getByText('FREE TRIAL');
    expect(el.className).toMatch(/bg-muted/);
  });

  it('applies md size classes when size="md"', () => {
    render(<PlanBadge plan="SMALL_BUSINESS" size="md" />);
    const el = screen.getByText('Small Business');
    expect(el.className).toMatch(/px-3/);
    expect(el.className).toMatch(/py-1\.5/);
  });
});
