import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatCurrency(amount: number, currency: string = 'LKR'): string {
  const locale = currency === 'LKR' ? 'en-LK' : 'en-US';
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/**
 * Centralized currency configuration.
 * Change CURRENCY_CODE to switch the entire application's currency.
 * Supported: 'LKR' (Sri Lankan Rupee) | 'USD' (US Dollar)
 */
export const CURRENCY = {
  code: 'LKR' as const,
  symbol: 'Rs.',
  locale: 'en-LK',
} as const;

/** Store calendar zone — timestamps render in this zone, not the device's, so a
 *  tablet set to another timezone still prints store-local receipt/report times. */
export const STORE_TIMEZONE = 'Asia/Colombo';

/** Shorthand: format a number using the app's primary currency */
export function fc(amount: number): string {
  return formatCurrency(amount, CURRENCY.code);
}

/**
 * Pulls a user-readable error message out of an Axios-style API error, with
 * fallback. Use this at every API call-site instead of re-implementing the
 * `(err as { response?: { data?: ... } })?.message` cast.
 */
export function getApiErrorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (typeof err === 'object' && err !== null) {
    const maybe = err as { response?: { data?: { message?: unknown } }; message?: unknown };
    const apiMsg = maybe.response?.data?.message;
    if (typeof apiMsg === 'string' && apiMsg.trim().length > 0) return apiMsg;
    if (typeof maybe.message === 'string' && maybe.message.trim().length > 0) return maybe.message;
  }
  if (typeof err === 'string' && err.trim().length > 0) return err;
  return fallback;
}

export function formatDate(date: string | Date, options?: Intl.DateTimeFormatOptions): string {
  const defaultOptions: Intl.DateTimeFormatOptions = {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZone: STORE_TIMEZONE,
  };
  // toLocaleString (not toLocaleDateString, which silently drops the time fields)
  // so hour/minute actually render, and always pin the store timezone unless the
  // caller explicitly overrides it.
  const opts: Intl.DateTimeFormatOptions = options
    ? { timeZone: STORE_TIMEZONE, ...options }
    : defaultOptions;
  return new Date(date).toLocaleString('en-US', opts);
}
