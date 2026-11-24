import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'mc-kpi-card',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <div class="kpi-card mc-card mc-flex-col">
      <div class="kpi-header mc-flex-row">
        <span class="kpi-title">{{ title() }}</span>
        @if (icon()) {
          <div class="kpi-icon-wrap">
            <mat-icon class="kpi-icon">{{ icon() }}</mat-icon>
          </div>
        }
      </div>
      <div class="kpi-body">
        <span class="kpi-value tabular-nums mono">{{ value() }}</span>
      </div>
      @if (subtitle()) {
        <div class="kpi-footer">
          <span class="kpi-subtitle">{{ subtitle() }}</span>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .kpi-card {
        padding: 22px 24px;
        min-width: 200px;
        border-radius: var(--mc-radius);
        position: relative;
        overflow: hidden;
      }
      .kpi-header {
        justify-content: space-between;
        margin-bottom: 12px;
      }
      .kpi-title {
        font-size: 0.8125rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.06em;
        color: var(--mc-text-secondary);
      }
      .kpi-icon-wrap {
        width: 36px;
        height: 36px;
        border-radius: 10px;
        background: rgba(0, 216, 246, 0.12);
        border: 1px solid rgba(0, 216, 246, 0.25);
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .kpi-icon {
        font-size: 20px;
        width: 20px;
        height: 20px;
        color: var(--mc-primary);
      }
      .kpi-body {
        margin: 4px 0 8px 0;
      }
      .kpi-value {
        font-size: 2.15rem;
        font-weight: 800;
        color: var(--mc-text-primary);
        line-height: 1.15;
        letter-spacing: -0.02em;
      }
      .kpi-footer {
        margin-top: 2px;
      }
      .kpi-subtitle {
        font-size: 0.8125rem;
        font-weight: 500;
        color: var(--mc-text-muted);
      }

      @media (max-width: 600px) {
        .kpi-card {
          padding: 18px 16px;
        }
        .kpi-value {
          font-size: 1.75rem;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class KpiCardComponent {
  readonly title = input.required<string>();
  readonly value = input.required<string | number>();
  readonly subtitle = input<string>();
  readonly icon = input<string>();
}
