import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'mc-error-state',
  standalone: true,
  imports: [MatIconModule, MatButtonModule],
  template: `
    <div class="error-state mc-flex-col" role="alert">
      <div class="error-icon-wrap">
        <mat-icon class="error-icon">error_outline</mat-icon>
      </div>
      <h3 class="error-title">{{ title() }}</h3>
      <p class="error-message">{{ message() }}</p>
      @if (requestId()) {
        <span class="request-id mono">Reference: {{ requestId() }}</span>
      }
      <button mat-stroked-button color="warn" class="retry-button" (click)="retry.emit()">
        <mat-icon>refresh</mat-icon>
        {{ retryLabel() }}
      </button>
    </div>
  `,
  styles: [
    `
      .error-state {
        align-items: center;
        justify-content: center;
        padding: 48px 24px;
        text-align: center;
        background: var(--mc-danger-bg);
        border: 1px solid var(--mc-danger-border);
        border-radius: var(--mc-radius);
      }
      .error-icon-wrap {
        width: 56px;
        height: 56px;
        border-radius: 50%;
        background: #fee2e2;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: 16px;
      }
      .error-icon {
        font-size: 28px;
        width: 28px;
        height: 28px;
        color: var(--mc-danger);
      }
      .error-title {
        margin: 0 0 8px 0;
        font-size: 1.125rem;
        font-weight: 600;
        color: var(--mc-danger);
      }
      .error-message {
        margin: 0 0 12px 0;
        font-size: 0.875rem;
        color: var(--mc-text-secondary);
        max-width: 480px;
      }
      .request-id {
        font-size: 0.75rem;
        color: var(--mc-text-muted);
        margin-bottom: 20px;
        background: #fff;
        padding: 2px 8px;
        border-radius: 4px;
        border: 1px solid var(--mc-border);
      }
      .retry-button {
        border-radius: var(--mc-radius-sm);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ErrorStateComponent {
  readonly title = input<string>('Unable to load data');
  readonly message = input.required<string>();
  readonly requestId = input<string>();
  readonly retryLabel = input<string>('Retry');

  readonly retry = output<void>();
}
