import { centsOf, priceError } from './money';

describe('money helpers', () => {
  it('reads typed prices exactly, without floating-point drift', () => {
    expect(centsOf('42.5')).toBe(4250);
    expect(centsOf(42.5)).toBe(4250);
    expect(centsOf('0.29')).toBe(29); // 0.29 * 100 would give 28.999999999999996
    expect(centsOf('19,99')).toBe(1999); // Romanian decimal comma
    expect(centsOf('7')).toBe(700);
  });

  it('refuses what the API would reject', () => {
    expect(centsOf('12.345')).toBeNull();
    expect(centsOf('-1')).toBeNull();
    expect(centsOf('abc')).toBeNull();
    expect(centsOf('')).toBeNull();
    expect(centsOf(null)).toBeNull();
    expect(centsOf('100000000')).toBeNull();
  });

  it('explains the problem in the form', () => {
    expect(priceError('12.345')).toContain('at most two decimals');
    expect(priceError(0)).toBe('Price must be greater than 0.');
    expect(priceError(0, { allowZero: true })).toBeNull();
    expect(priceError('42.50')).toBeNull();
  });
});
