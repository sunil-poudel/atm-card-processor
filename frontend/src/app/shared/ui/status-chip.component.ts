import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type StatusType =
  | 'APPROVED'
  | 'DECLINED'
  | 'FLAGGED'
  | 'ACTIVE'
  | 'INACTIVE'
  | 'BLOCKED'
  | 'CLOSED'
  | 'OPEN'
  | 'EVIDENCE_REQUESTED'
  | 'EVIDENCE_SUBMITTED'
  | 'RESOLVED_WON'
  | 'RESOLVED_LOST'
  | 'ENABLED'
  | 'DISABLED'
  | 'REVOKED'
  | 'LOCKED'
  | string;

@Component({
  selector: 'mc-status-chip',
  standalone: true,
  template: `
    <span class="status-chip {{ chipClass() }}" [attr.aria-label]="'Status: ' + label()">
      <span class="status-dot"></span>
      <span class="status-text">{{ label() }}</span>
    </span>
  `,
  styles: [
    `
      .status-chip {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 3px 10px;
        border-radius: 9999px;
        font-size: 0.75rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        line-height: 1.4;
        border: 1px solid transparent;
        white-space: nowrap;
      }
      .status-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
      }

      /* Success Green (Approved, Active, Enabled) */
      .chip-success {
        background-color: var(--mc-success-bg);
        color: var(--mc-success);
        border-color: var(--mc-success-border);
      }
      .chip-success .status-dot {
        background-color: var(--mc-success);
        box-shadow: 0 0 6px var(--mc-success);
      }

      /* Danger Red (Declined, Blocked, Revoked) */
      .chip-danger {
        background-color: var(--mc-danger-bg);
        color: var(--mc-danger);
        border-color: var(--mc-danger-border);
      }
      .chip-danger .status-dot {
        background-color: var(--mc-danger);
        box-shadow: 0 0 6px var(--mc-danger);
      }

      /* Warning Amber (Flagged, Evidence Requested) */
      .chip-warning {
        background-color: var(--mc-warning-bg);
        color: var(--mc-warning);
        border-color: var(--mc-warning-border);
      }
      .chip-warning .status-dot {
        background-color: var(--mc-warning);
        box-shadow: 0 0 6px var(--mc-warning);
      }

      /* Neutral Slate (Inactive, Closed, Disabled) */
      .chip-neutral {
        background-color: var(--mc-neutral-bg);
        color: var(--mc-neutral);
        border-color: var(--mc-neutral-border);
      }
      .chip-neutral .status-dot {
        background-color: var(--mc-neutral);
      }

      /* Info Cyan / Blue (Open, Evidence Submitted) */
      .chip-info {
        background-color: rgba(0, 216, 246, 0.12);
        color: #00d8f6;
        border-color: rgba(0, 216, 246, 0.3);
      }
      .chip-info .status-dot {
        background-color: #00d8f6;
        box-shadow: 0 0 6px #00d8f6;
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusChipComponent {
  readonly status = input.required<StatusType>();
  readonly customLabel = input<string>();

  readonly label = computed(() => {
    if (this.customLabel()) {
      return this.customLabel()!;
    }
    const s = this.status() || '';
    return s.replace(/_/g, ' ');
  });

  readonly chipClass = computed(() => {
    const s = (this.status() || '').toUpperCase();
    switch (s) {
      case 'APPROVED':
      case 'ACTIVE':
      case 'ENABLED':
      case 'RESOLVED_WON':
        return 'chip-success';

      case 'DECLINED':
      case 'BLOCKED':
      case 'RESOLVED_LOST':
      case 'REVOKED':
      case 'LOCKED':
        return 'chip-danger';

      case 'FLAGGED':
      case 'EVIDENCE_REQUESTED':
        return 'chip-warning';

      case 'EVIDENCE_SUBMITTED':
      case 'OPEN':
        return 'chip-info';

      case 'INACTIVE':
      case 'CLOSED':
      case 'DISABLED':
      default:
        return 'chip-neutral';
    }
  });
}
