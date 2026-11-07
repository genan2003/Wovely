/**
 * Money helpers (PLAN.md 7.5). The API stores whole cents and rejects amounts with more than two decimals
 * instead of rounding them, so forms check that before sending.
 */

/** Cents of an amount typed into a form, or null when it isn't a valid price (negative, NaN, > 2 decimals). */
export function centsOf(amount: number | string | null | undefined): number | null {
  if (amount === null || amount === undefined || amount === '') return null;
  const text = String(amount).trim();
  // the typed text, not floating-point maths: 0.29 * 100 is 28.999999999999996
  const match = /^(\d{1,7})(?:[.,](\d{1,2}))?$/.exec(text);
  if (!match) return null;
  const whole = Number(match[1]);
  const fraction = Number((match[2] ?? '').padEnd(2, '0'));
  return whole * 100 + fraction;
}

/** Message for a price field, or null when the value is fine. */
export function priceError(amount: number | string | null | undefined, { allowZero = false } = {}): string | null {
  const cents = centsOf(amount);
  if (cents === null) return 'Enter a price in RON with at most two decimals, for example 42.50.';
  if (cents === 0 && !allowZero) return 'Price must be greater than 0.';
  return null;
}
