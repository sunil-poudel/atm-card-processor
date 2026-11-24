import { minorToMajorString, parseMajorToMinor } from './money-parser';

describe('money-parser', () => {
  describe('parseMajorToMinor', () => {
    it('should parse integer major units to minor units', () => {
      expect(parseMajorToMinor('100')).toBe(10000);
      expect(parseMajorToMinor('1')).toBe(100);
      expect(parseMajorToMinor('0')).toBe(0);
    });

    it('should parse decimals with one or two digits', () => {
      expect(parseMajorToMinor('12.34')).toBe(1234);
      expect(parseMajorToMinor('12.3')).toBe(1230);
      expect(parseMajorToMinor('0.05')).toBe(5);
      expect(parseMajorToMinor('0.50')).toBe(50);
      expect(parseMajorToMinor('0.00')).toBe(0);
    });

    it('should reject inputs with more than two decimal places', () => {
      expect(() => parseMajorToMinor('12.345')).toThrowError(
        /Amount cannot have more than 2 decimal places/,
      );
      expect(() => parseMajorToMinor('0.001')).toThrowError(
        /Amount cannot have more than 2 decimal places/,
      );
    });

    it('should reject negative amounts', () => {
      expect(() => parseMajorToMinor('-10')).toThrowError(/Amount cannot be negative/);
      expect(() => parseMajorToMinor('-0.01')).toThrowError(/Amount cannot be negative/);
    });

    it('should reject invalid or non-numeric inputs', () => {
      expect(() => parseMajorToMinor('')).toThrowError(/Amount is required/);
      expect(() => parseMajorToMinor('   ')).toThrowError(/Amount is required/);
      expect(() => parseMajorToMinor('abc')).toThrowError(/Invalid numeric amount/);
      expect(() => parseMajorToMinor('12.34.56')).toThrowError(/Invalid numeric amount/);
      expect(() => parseMajorToMinor('12,34')).toThrowError(/Invalid numeric amount/);
    });

    it('should support numeric inputs directly', () => {
      expect(parseMajorToMinor(50)).toBe(5000);
      expect(parseMajorToMinor(12.34)).toBe(1234);
    });
  });

  describe('minorToMajorString', () => {
    it('should convert minor units to 2-decimal string', () => {
      expect(minorToMajorString(1234)).toBe('12.34');
      expect(minorToMajorString(1234n)).toBe('12.34');
      expect(minorToMajorString(5)).toBe('0.05');
      expect(minorToMajorString(50)).toBe('0.50');
      expect(minorToMajorString(0)).toBe('0.00');
      expect(minorToMajorString(50000)).toBe('500.00');
    });

    it('should handle negative minor units', () => {
      expect(minorToMajorString(-1234)).toBe('-12.34');
      expect(minorToMajorString(-5)).toBe('-0.05');
    });
  });
});
