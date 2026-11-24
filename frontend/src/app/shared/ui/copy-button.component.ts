import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NotificationService } from '../../core/notify/notification.service';

@Component({
  selector: 'mc-copy-button',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  template: `
    <button
      mat-icon-button
      type="button"
      [matTooltip]="copied() ? 'Copied!' : tooltip()"
      [attr.aria-label]="copied() ? 'Copied to clipboard' : tooltip()"
      (click)="copyToClipboard($event)"
      class="copy-btn"
    >
      <mat-icon class="copy-icon">{{ copied() ? 'check' : 'content_copy' }}</mat-icon>
    </button>
  `,
  styles: [
    `
      .copy-btn {
        width: 32px;
        height: 32px;
        line-height: 32px;
      }
      .copy-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: var(--mc-text-secondary);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CopyButtonComponent {
  private readonly notify = inject(NotificationService);

  readonly value = input.required<string>();
  readonly tooltip = input<string>('Copy to clipboard');
  readonly showToast = input<boolean>(false);

  readonly copied = signal<boolean>(false);

  async copyToClipboard(event: Event): Promise<void> {
    event.stopPropagation();
    try {
      await navigator.clipboard.writeText(this.value());
      this.copied.set(true);
      if (this.showToast()) {
        this.notify.success('Copied to clipboard');
      }
      setTimeout(() => {
        this.copied.set(false);
      }, 2000);
    } catch {
      this.notify.error('Failed to copy to clipboard');
    }
  }
}
