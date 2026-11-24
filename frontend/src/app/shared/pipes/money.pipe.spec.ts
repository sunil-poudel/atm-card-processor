import { MoneyPipe } from './money.pipe';

describe('MoneyPipe', () => {
  let pipe: MoneyPipe;

  beforeEach(() => {
    pipe = new MoneyPipe();
  });

  it('should format minor units with currency code', () => {
    expect(pipe.transform(50000, 'NPR')).toBe('NPR 500.00');
    expect(pipe.transform(1234, 'USD')).toBe('USD 12.34');
    expect(pipe.transform(0, 'NPR')).toBe('NPR 0.00');
  });

  it('should support bigint values', () => {
    expect(pipe.transform(1000000n, 'NPR')).toBe('NPR 10,000.00');
  });

  it('should handle null, undefined, or NaN gracefully', () => {
    expect(pipe.transform(null)).toBe('-');
    expect(pipe.transform(undefined)).toBe('-');
    expect(pipe.transform(NaN)).toBe('-');
  });

  it('should format negative amounts correctly', () => {
    expect(pipe.transform(-1234, 'USD')).toBe('-USD 12.34');
  });

  it('should support showSign for positive values', () => {
    expect(pipe.transform(1234, 'USD', true)).toBe('+USD 12.34');
    expect(pipe.transform(0, 'USD', true)).toBe('USD 0.00');
    expect(pipe.transform(-1234, 'USD', true)).toBe('-USD 12.34');
  });

  it('should default currency to NPR', () => {
    expect(pipe.transform(500)).toBe('NPR 5.00');
  });
});
