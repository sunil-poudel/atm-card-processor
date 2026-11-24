import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'money',
  standalone: true,
})
export class MoneyPipe implements PipeTransform {
  transform(
    amountMinor: number | bigint | null | undefined,
    currency = 'NPR',
    showSign = false,
  ): string {
    if (amountMinor === null || amountMinor === undefined) {
      return '-';
    }

    const minor = typeof amountMinor === 'bigint' ? Number(amountMinor) : amountMinor;
    if (isNaN(minor)) {
      return '-';
    }

    const isNegative = minor < 0;
    const absMajor = Math.abs(minor) / 100;

    const formattedNumber = new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(absMajor);

    const curr = (currency || 'NPR').toUpperCase();
    if (isNegative) {
      return `-${curr} ${formattedNumber}`;
    }
    if (showSign && minor > 0) {
      return `+${curr} ${formattedNumber}`;
    }
    return `${curr} ${formattedNumber}`;
  }
}
