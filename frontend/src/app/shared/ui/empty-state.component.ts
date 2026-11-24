import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'mc-empty-state',
  standalone: true,
  imports: [MatIconModule, MatButtonModule],
  template: `
    <div class="empty-state mc-flex-col">
      <div class="empty-icon-wrap">
        <mat-icon class="empty-icon">{{ icon() }}</mat-icon>
      </div>
      <h3 class="empty-title">{{ title() }}</h3>
      <p class="empty-message">{{ message() }}</p>
      @if (actionLabel()) {
        <button mat-flat-button color="primary" class="empty-action" (click)="action.emit()">
          @if (actionIcon()) {
            <mat-icon>{{ actionIcon() }}</mat-icon>
          }
          {{ actionLabel() }}
        </button>
      }
    </div>
  `,
  styles: [
    `
      .empty-state {
        align-items: center;
        justify-content: center;
        padding: 48px 24px;
        text-align: center;
        background: var(--mc-surface);
        border: 1px dashed var(--mc-border);
        border-radius: var(--mc-radius);
      }
      .empty-icon-wrap {
        width: 56px;
        height: 56px;
        border-radius: 50%;
        background: var(--mc-surface-hover);
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: 16px;
      }
      .empty-icon {
        font-size: 28px;
        width: 28px;
        height: 28px;
        color: var(--mc-text-muted);
      }
      .empty-title {
        margin: 0 0 8px 0;
        font-size: 1.125rem;
        font-weight: 600;
        color: var(--mc-text-primary);
      }
      .empty-message {
        margin: 0 0 20px 0;
        font-size: 0.875rem;
        color: var(--mc-text-secondary);
        max-width: 400px;
      }
      .empty-action {
        border-radius: var(--mc-radius-sm);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyStateComponent {
  readonly icon = input<string>('inbox');
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly actionLabel = input<string>();
  readonly actionIcon = input<string>();

  readonly action = output<void>();
}
