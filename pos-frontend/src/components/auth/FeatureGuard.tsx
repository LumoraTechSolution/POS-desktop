"use client";

import React from 'react';
import { useAuthStore } from '@/stores/authStore';

interface FeatureGuardProps {
  feature: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

/**
 * Conditionally renders UI components based on the Tenant's SaaS Plan active features.
 * Prevents UI leakage for features the tenant hasn't paid for / subscribed to.
 */
export function FeatureGuard({ feature, children, fallback = null }: FeatureGuardProps) {
  const hasFeature = useAuthStore((state) => state.hasFeature);

  if (hasFeature(feature)) {
    return <>{children}</>;
  }

  return <>{fallback}</>;
}
