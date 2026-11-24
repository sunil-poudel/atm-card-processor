import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';

@Component({
  selector: 'mc-loading-skeleton',
  standalone: true,
  imports: [MatProgressBarModule],
  template: `
    <div class="skeleton-wrap">
      <mat-progress-bar mode="indeterminate" class="progress-bar"></mat-progress-bar>
      @for (row of rowsArray; track $index) {
        <div class="skeleton-row" [style.height.px]="rowHeight()"></div>
      }
    </div>
  `,
  styles: [
    `
      .skeleton-wrap {
        display: flex;
        flex-direction: column;
        gap: 12px;
        padding: 16px 0;
      }
      .progress-bar {
        border-radius: 4px;
        margin-bottom: 8px;
      }
      .skeleton-row {
        width: 100%;
        background: linear-gradient(
          90deg,
          var(--mc-surface-hover) 25%,
          #e2e8f0 50%,
          var(--mc-surface-hover) 75%
        );
        background-size: 200% 100%;
        animation: pulse 1.5s infinite;
        border-radius: 6px;
      }
      @keyframes pulse {
        0% {
          background-position: 200% 0;
        }
        100% {
          background-position: -200% 0;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoadingSkeletonComponent {
  readonly rows = input<number>(4);
  readonly rowHeight = input<number>(36);

  get rowsArray(): number[] {
    return Array.from({ length: this.rows() }, (_, i) => i);
  }
}
