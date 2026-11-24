export class MoneyParseError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'MoneyParseError';
  }
}

/**
 * Converts a major unit currency amount (e.g. 12.34 or "12.34") to minor units (1234).
 * Rejects negative numbers, NaN, non-numeric strings, and numbers with more than 2 decimal places.
 */
export function parseMajorToMinor(val: string | number | null | undefined): number {
  if (val === null || val === undefined) {
    throw new MoneyParseError('Amount is required');
  }

  const str = String(val).trim();
  if (str === '') {
    throw new MoneyParseError('Amount is required');
  }

  // Allow only digits and at most one decimal point
  if (!/^\d+(\.\d{1,2})?$/.test(str)) {
    if (/^\d+\.\d{3,}$/.test(str)) {
      throw new MoneyParseError('Amount cannot have more than 2 decimal places');
    }
    if (str.startsWith('-')) {
      throw new MoneyParseError('Amount cannot be negative');
    }
    throw new MoneyParseError('Invalid numeric amount');
  }

  const parts = str.split('.');
  const wholePart = BigInt(parts[0]);
  const fracPart = parts.length > 1 ? parts[1].padEnd(2, '0') : '00';
  const minorBig = wholePart * 100n + BigInt(fracPart);

  // JavaScript MAX_SAFE_INTEGER is 9,007,199,254,740,991, which easily handles standard currency minor units
  if (minorBig > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new MoneyParseError('Amount exceeds maximum allowable limit');
  }

  return Number(minorBig);
}

/**
 * Formats a minor unit amount to a major unit string suitable for input fields (e.g. 1234 -> "12.34").
 */
export function minorToMajorString(minor: number | bigint | null | undefined): string {
  if (minor === null || minor === undefined || isNaN(Number(minor))) {
    return '0.00';
  }
  const isNegative = minor < 0;
  const absMinor = isNegative ? -minor : minor;
  const whole = Math.floor(Number(absMinor) / 100);
  const frac = String(Number(absMinor) % 100).padStart(2, '0');
  return `${isNegative ? '-' : ''}${whole}.${frac}`;
}
