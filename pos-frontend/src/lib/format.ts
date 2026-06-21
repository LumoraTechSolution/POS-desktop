/**
 * Formats a number as currency in the tenant's locale.
 *
 * Currency is hardcoded to LKR for now — when multi-currency support lands,
 * read from tenant settings or pass an explicit currency code.
 */
export function formatCurrency(amount: number, currency: string = 'LKR', locale: string = 'en-LK'): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
  }).format(amount);
}

export function formatCurrencyCompact(amount: number, currency: string = 'LKR', locale: string = 'en-LK'): string {
  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(amount);
}
