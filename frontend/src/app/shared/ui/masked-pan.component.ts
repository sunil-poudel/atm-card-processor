import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'mc-masked-pan',
  standalone: true,
  template: `
    <span class="masked-pan mono" [attr.aria-label]="'Card PAN ' + pan()">
      {{ formattedPan }}
    </span>
  `,
  styles: [
    `
      .masked-pan {
        letter-spacing: 0.12em;
        font-weight: 700;
        color: var(--mc-text-primary);
        user-select: none;
        font-size: 0.9375rem;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MaskedPanComponent {
  readonly pan = input.required<string>();

  get formattedPan(): string {
    const raw = this.pan() || '';
    // Format into 4-char groups if 16 chars: 4111 11** **** 1234
    if (raw.length === 16 && !raw.includes(' ')) {
      return `${raw.slice(0, 4)} ${raw.slice(4, 8)} ${raw.slice(8, 12)} ${raw.slice(12, 16)}`;
    }
    return raw;
  }
}
