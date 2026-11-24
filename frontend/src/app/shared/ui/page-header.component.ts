import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'mc-page-header',
  standalone: true,
  template: `
    <header class="page-header">
      <div class="header-titles">
        <h1 class="page-title">{{ title() }}</h1>
        @if (subtitle()) {
          <p class="page-subtitle">{{ subtitle() }}</p>
        }
      </div>
      <div class="header-actions">
        <ng-content select="[actions]"></ng-content>
        <ng-content></ng-content>
      </div>
    </header>
  `,
  styles: [
    `
      .page-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        margin-bottom: 24px;
        flex-wrap: wrap;
        gap: 16px;
      }
      .header-titles {
        flex: 1 1 300px;
      }
      .page-title {
        margin: 0;
        font-size: 1.85rem;
        font-weight: 800;
        letter-spacing: -0.03em;
        line-height: 1.2;
        color: var(--mc-text-primary);
      }
      .page-subtitle {
        margin: 6px 0 0 0;
        font-size: 0.9375rem;
        font-weight: 500;
        color: var(--mc-text-secondary);
        line-height: 1.4;
      }
      .header-actions {
        display: flex;
        align-items: center;
        gap: 12px;
        flex-wrap: wrap;
      }

      @media (max-width: 600px) {
        .page-header {
          margin-bottom: 18px;
          gap: 12px;
        }
        .page-title {
          font-size: 1.45rem;
        }
        .page-subtitle {
          font-size: 0.8125rem;
        }
        .header-actions {
          width: 100%;
          justify-content: flex-start;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
