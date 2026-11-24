import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NotificationService } from '../../../core/notify/notification.service';
import { ReferenceDataService } from '../../../core/reference/reference-data.service';
import { parseMajorToMinor } from '../../../shared/forms/money-parser';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { CopyButtonComponent } from '../../../shared/ui/copy-button.component';
import { MaskedPanComponent } from '../../../shared/ui/masked-pan.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../../shared/ui/status-chip.component';
import { CardResponse, CardsApiService } from '../../cards/cards-api.service';
import { TransactionDetailDialogComponent } from '../dialogs/transaction-detail-dialog.component';
import {
  AuthorizeRequest,
  AuthorizeResult,
  TransactionsApiService,
} from '../transactions-api.service';

function generateUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export interface RapidFireItem {
  index: number;
  idempotencyKey: string;
  status?: string;
  responseCode?: string;
  transactionId?: string;
  replayed?: boolean;
  error?: string;
}

@Component({
  selector: 'mc-payment-simulator',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    MatTooltipModule,
    PageHeaderComponent,
    StatusChipComponent,
    MaskedPanComponent,
    CopyButtonComponent,
    MoneyPipe,
  ],
  template: `
    <mc-page-header
      title="Payment Simulator"
      subtitle="Simulate point-of-sale authorizations with full idempotency and fraud controls"
    >
      <div actions class="header-badges">
        <span class="test-tool-badge">
          <mat-icon>science</mat-icon>
          <span>Testing & Demo Tool</span>
        </span>
      </div>
    </mc-page-header>

    <div class="simulator-layout">
      <!-- Left Column: Form & Presets -->
      <div class="form-column">
        <!-- Quick Presets -->
        <mat-card class="preset-card">
          <mat-card-header>
            <mat-card-title>Quick Presets</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="preset-buttons">
              <button
                mat-stroked-button
                type="button"
                (click)="applyPreset('grocery')"
                [disabled]="submitting() || rapidFiring()"
              >
                <mat-icon color="primary">shopping_cart</mat-icon>
                <span>Grocery 500 (MCC 5411)</span>
              </button>

              <button
                mat-stroked-button
                type="button"
                (click)="applyPreset('betting')"
                [disabled]="submitting() || rapidFiring()"
              >
                <mat-icon color="warn">casino</mat-icon>
                <span>Betting 100 (MCC 7995)</span>
              </button>

              <button
                mat-stroked-button
                type="button"
                color="accent"
                (click)="triggerRapidFire()"
                [disabled]="submitting() || rapidFiring() || !form.value.cardId"
                matTooltip="Fires 6 sequential authorizations with distinct keys to test velocity rules"
              >
                @if (rapidFiring()) {
                  <mat-spinner diameter="16"></mat-spinner>
                  <span>Rapid-firing {{ rapidFireCurrent() }}/6...</span>
                } @else {
                  <ng-container>
                    <mat-icon>flash_on</mat-icon>
                    <span>Rapid-fire x6</span>
                  </ng-container>
                }
              </button>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Main Form -->
        <mat-card class="form-card">
          <mat-card-content>
            <form [formGroup]="form" (ngSubmit)="submit()" class="sim-form">
              <!-- Card Selector -->
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Select Active Card</mat-label>
                <mat-select
                  formControlName="cardId"
                  (selectionChange)="onCardSelected($event.value)"
                >
                  @for (c of activeCards(); track c.id) {
                    <mat-option [value]="c.id">
                      {{ c.holderName }} &bull; •••• {{ c.maskedPan?.slice(-4) }} ({{
                        c.balanceMinor | money: c.currency
                      }})
                    </mat-option>
                  }
                </mat-select>
                @if (form.get('cardId')?.hasError('required')) {
                  <mat-error>Card is required</mat-error>
                }
              </mat-form-field>

              @if (selectedCard(); as card) {
                <div class="card-info-banner">
                  <div class="card-info-item">
                    <span class="info-label">Cardholder</span>
                    <span class="info-val">{{ card.holderName }}</span>
                  </div>
                  <div class="card-info-item">
                    <span class="info-label">PAN</span>
                    <mc-masked-pan [pan]="card.maskedPan || ''"></mc-masked-pan>
                  </div>
                  <div class="card-info-item">
                    <span class="info-label">Current Balance</span>
                    <span class="info-val font-semibold tabular-nums">
                      {{ card.balanceMinor | money: card.currency }}
                    </span>
                  </div>
                  <div class="card-info-item">
                    <span class="info-label">Currency</span>
                    <span class="info-val font-semibold">{{ card.currency }}</span>
                  </div>
                </div>
              }

              <!-- Amount & Currency -->
              <div class="form-row">
                <mat-form-field appearance="outline" class="flex-2">
                  <mat-label>Amount (major units)</mat-label>
                  <input matInput type="text" formControlName="amount" placeholder="e.g. 500.00" />
                  @if (form.get('amount')?.hasError('required')) {
                    <mat-error>Amount is required</mat-error>
                  } @else if (form.get('amount')?.hasError('invalidMoney')) {
                    <mat-error>Invalid amount format (up to 2 decimals)</mat-error>
                  }
                </mat-form-field>

                <mat-form-field appearance="outline" class="flex-1">
                  <mat-label>Currency</mat-label>
                  <mat-select formControlName="currency">
                    @for (curr of currencies(); track curr) {
                      <mat-option [value]="curr">{{ curr }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              </div>

              <!-- Merchant Name -->
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Merchant Name</mat-label>
                <input
                  matInput
                  type="text"
                  formControlName="merchantName"
                  placeholder="e.g. Bhatbhateni Supermarket"
                />
                @if (form.get('merchantName')?.hasError('required')) {
                  <mat-error>Merchant name is required</mat-error>
                }
              </mat-form-field>

              <!-- MCC Selection -->
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Merchant Category Code (MCC)</mat-label>
                <mat-select formControlName="mcc">
                  @for (opt of mccOptions(); track opt.code) {
                    <mat-option [value]="opt.code"> {{ opt.code }} ({{ opt.label }}) </mat-option>
                  }
                </mat-select>
                @if (form.get('mcc')?.hasError('required')) {
                  <mat-error>MCC is required</mat-error>
                }
              </mat-form-field>

              <!-- Reference -->
              <mat-form-field appearance="outline" class="full-width">
                <mat-label>Reference (optional)</mat-label>
                <input
                  matInput
                  type="text"
                  formControlName="reference"
                  placeholder="e.g. POS-REF-001"
                />
              </mat-form-field>

              <!-- Idempotency Key -->
              <div class="idempotency-box">
                <div class="idempotency-text">
                  <span class="label">Idempotency Key:</span>
                  <span class="key mono">{{ currentKey() }}</span>
                </div>
                <div class="key-actions">
                  <mc-copy-button [value]="currentKey()"></mc-copy-button>
                  <button
                    mat-icon-button
                    type="button"
                    (click)="generateNewKey()"
                    matTooltip="Generate new key"
                    aria-label="Generate new key"
                    [disabled]="submitting() || rapidFiring()"
                  >
                    <mat-icon>refresh</mat-icon>
                  </button>
                </div>
              </div>

              <!-- Submit Button -->
              <div class="form-actions">
                <button
                  mat-flat-button
                  color="primary"
                  type="submit"
                  class="submit-btn"
                  [disabled]="form.invalid || submitting() || rapidFiring()"
                >
                  @if (submitting()) {
                    <mat-spinner diameter="18"></mat-spinner>
                    <span>Authorizing...</span>
                  } @else {
                    <ng-container>
                      <mat-icon>send</mat-icon>
                      <span>Send Authorization</span>
                    </ng-container>
                  }
                </button>
              </div>
            </form>
          </mat-card-content>
        </mat-card>
      </div>

      <!-- Right Column: Result Panel -->
      <div class="result-column">
        @if (result(); as res) {
          <mat-card class="result-card {{ res.body.status?.toLowerCase() }}">
            <div class="result-banner">
              <div class="banner-status">
                <mat-icon class="status-big-icon">
                  {{ res.body.status === 'APPROVED' ? 'check_circle' : 'cancel' }}
                </mat-icon>
                <div class="status-text-wrap">
                  <h2 class="status-heading">{{ res.body.status }}</h2>
                  <span class="code-subtitle">
                    Code {{ res.body.responseCode }} &bull; {{ res.body.responseMessage }}
                  </span>
                </div>
              </div>
              <mc-status-chip [status]="res.body.status || 'UNKNOWN'"></mc-status-chip>
            </div>

            <mat-card-content class="result-content">
              @if (res.replayed) {
                <div class="replayed-banner">
                  <mat-icon>repeat</mat-icon>
                  <div>
                    <strong>Idempotent Replay Detected</strong>
                    <p>
                      The server recognized this Idempotency-Key and returned the cached
                      authorization response without re-processing.
                    </p>
                  </div>
                </div>
              }

              <div class="result-grid">
                <div class="grid-item">
                  <span class="label">Response Code</span>
                  <span class="val font-semibold mono">{{ res.body.responseCode }}</span>
                </div>

                <div class="grid-item">
                  <span class="label">Response Message</span>
                  <span class="val">{{ res.body.responseMessage }}</span>
                </div>

                <div class="grid-item">
                  <span class="label">Authorized At</span>
                  <span class="val">
                    {{ res.body.authorizedAt | date: 'yyyy-MM-dd HH:mm:ss' : '+0545' }}
                  </span>
                </div>

                <div class="grid-item">
                  <span class="label">Transaction ID</span>
                  <div class="val copy-cell">
                    @if (res.body.transactionId) {
                      <button
                        mat-button
                        color="primary"
                        type="button"
                        class="tx-btn"
                        (click)="viewTransaction(res.body.transactionId)"
                      >
                        <mat-icon>open_in_new</mat-icon>
                        <span class="mono">{{ res.body.transactionId.slice(0, 8) }}...</span>
                      </button>
                      <mc-copy-button [value]="res.body.transactionId"></mc-copy-button>
                    } @else {
                      <span>-</span>
                    }
                  </div>
                </div>

                <div class="grid-item full">
                  <span class="label">Idempotency-Key Used</span>
                  <div class="val copy-cell mono">
                    <span>{{ res.idempotencyKey }}</span>
                    <mc-copy-button [value]="res.idempotencyKey"></mc-copy-button>
                  </div>
                </div>
              </div>

              <!-- Replay Button -->
              <div class="replay-action">
                <button
                  mat-stroked-button
                  color="accent"
                  type="button"
                  (click)="replayRequest()"
                  [disabled]="submitting() || rapidFiring()"
                  matTooltip="Resends the exact same request with identical Idempotency-Key"
                >
                  <mat-icon>replay</mat-icon>
                  <span>Replay Same Request (Test Idempotency)</span>
                </button>
              </div>
            </mat-card-content>
          </mat-card>
        } @else {
          <mat-card class="empty-result-card">
            <mat-card-content class="empty-state">
              <mat-icon class="placeholder-icon">credit_card</mat-icon>
              <h3>No Authorization Sent Yet</h3>
              <p>
                Fill out the transaction details or select a quick preset on the left, then click
                <strong>Send Authorization</strong>.
              </p>
            </mat-card-content>
          </mat-card>
        }

        <!-- Rapid Fire Execution Results -->
        @if (rapidFireResults().length > 0) {
          <mat-card class="rapid-fire-card">
            <mat-card-header>
              <mat-card-title>Rapid-Fire Results (Sequential Velocity Test)</mat-card-title>
            </mat-card-header>
            <mat-card-content>
              <table class="rf-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Status</th>
                    <th>Code</th>
                    <th>Key</th>
                    <th>Detail</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of rapidFireResults(); track item.index) {
                    <tr>
                      <td>{{ item.index }}</td>
                      <td>
                        @if (item.status) {
                          <mc-status-chip [status]="item.status"></mc-status-chip>
                        } @else if (item.error) {
                          <span class="error-text">ERROR</span>
                        } @else {
                          <mat-spinner diameter="14"></mat-spinner>
                        }
                      </td>
                      <td class="mono">{{ item.responseCode || '-' }}</td>
                      <td class="mono font-xs">{{ item.idempotencyKey.slice(0, 8) }}...</td>
                      <td>
                        @if (item.transactionId) {
                          <button
                            mat-icon-button
                            color="primary"
                            (click)="viewTransaction(item.transactionId)"
                            matTooltip="View transaction"
                          >
                            <mat-icon>visibility</mat-icon>
                          </button>
                        }
                        @if (item.error) {
                          <span class="error-detail">{{ item.error }}</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </mat-card-content>
          </mat-card>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .header-badges {
        display: flex;
        align-items: center;
      }
      .test-tool-badge {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        background: #fef3c7;
        color: #92400e;
        border: 1px solid #fcd34d;
        border-radius: 16px;
        padding: 4px 12px;
        font-size: 13px;
        font-weight: 600;
      }
      .test-tool-badge mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }

      .simulator-layout {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 24px;
      }

      @media (max-width: 960px) {
        .simulator-layout {
          grid-template-columns: 1fr;
        }
      }

      .preset-card {
        margin-bottom: 20px;
      }
      .preset-buttons {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        padding-top: 8px;
      }

      .form-card {
        margin-bottom: 24px;
      }
      .sim-form {
        display: flex;
        flex-direction: column;
        gap: 4px;
        padding-top: 12px;
      }
      .full-width {
        width: 100%;
      }
      .form-row {
        display: flex;
        gap: 16px;
      }
      @media (max-width: 600px) {
        .form-row {
          flex-direction: column;
          gap: 0;
        }
      }
      .flex-2 {
        flex: 2;
      }
      .flex-1 {
        flex: 1;
      }

      .card-info-banner {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 12px;
        background: var(--mc-surface-hover);
        border: 1px solid var(--mc-border);
        border-radius: 10px;
        padding: 14px 18px;
        margin-bottom: 18px;
      }
      .card-info-item {
        display: flex;
        flex-direction: column;
        gap: 3px;
      }
      .info-label {
        font-size: 0.6875rem;
        text-transform: uppercase;
        color: var(--mc-text-secondary);
        font-weight: 700;
        letter-spacing: 0.06em;
      }
      .info-val {
        font-size: 0.875rem;
        color: var(--mc-text-primary);
      }

      .idempotency-box {
        display: flex;
        justify-content: space-between;
        align-items: center;
        background: var(--mc-surface-hover);
        border: 1px solid var(--mc-border);
        border-radius: 8px;
        padding: 10px 14px;
        margin: 8px 0 16px 0;
      }
      .idempotency-text {
        display: flex;
        flex-direction: column;
        gap: 2px;
        overflow: hidden;
      }
      .idempotency-text .label {
        font-size: 0.6875rem;
        color: var(--mc-text-secondary);
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }
      .idempotency-text .key {
        font-size: 0.75rem;
        color: var(--mc-text-primary);
        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
      }
      .key-actions {
        display: flex;
        align-items: center;
        gap: 4px;
      }

      .form-actions {
        display: flex;
        justify-content: flex-end;
        margin-top: 10px;
      }
      .submit-btn {
        min-width: 180px;
        height: 46px;
        font-weight: 700;
      }

      /* Results */
      .result-card {
        border-radius: 16px !important;
        overflow: hidden;
      }
      .result-card.approved {
        border-color: var(--mc-success) !important;
      }
      .result-card.declined {
        border-color: var(--mc-danger) !important;
      }

      .result-banner {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 22px 26px;
        border-bottom: 1px solid var(--mc-border);
      }
      .approved .result-banner {
        background: var(--mc-success-bg);
      }
      .declined .result-banner {
        background: var(--mc-danger-bg);
      }

      .banner-status {
        display: flex;
        align-items: center;
        gap: 16px;
      }
      .status-big-icon {
        font-size: 40px;
        width: 40px;
        height: 40px;
      }
      .approved .status-big-icon {
        color: var(--mc-success);
      }
      .declined .status-big-icon {
        color: var(--mc-danger);
      }

      .status-heading {
        margin: 0;
        font-size: 1.5rem;
        font-weight: 800;
        letter-spacing: -0.02em;
        color: var(--mc-text-primary);
      }
      .code-subtitle {
        font-size: 0.8125rem;
        color: var(--mc-text-secondary);
        font-family: 'JetBrains Mono', monospace;
      }

      .result-content {
        padding: 24px;
      }

      .replayed-banner {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        background: rgba(0, 216, 246, 0.12);
        border: 1px solid rgba(0, 216, 246, 0.3);
        border-radius: 10px;
        padding: 14px 18px;
        margin-bottom: 20px;
        color: var(--mc-primary);
      }
      .replayed-banner mat-icon {
        color: var(--mc-primary);
      }
      .replayed-banner p {
        margin: 4px 0 0 0;
        font-size: 0.8125rem;
        color: var(--mc-text-primary);
      }

      .result-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 16px;
        background: var(--mc-surface-hover);
        border: 1px solid var(--mc-border);
        border-radius: 10px;
        padding: 18px;
      }
      @media (max-width: 600px) {
        .result-grid {
          grid-template-columns: 1fr;
        }
      }
      .grid-item {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
      .grid-item.full {
        grid-column: span 2;
      }
      @media (max-width: 600px) {
        .grid-item.full {
          grid-column: span 1;
        }
      }
      .grid-item .label {
        font-size: 0.6875rem;
        color: var(--mc-text-secondary);
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }
      .grid-item .val {
        font-size: 0.875rem;
        color: var(--mc-text-primary);
      }

      .copy-cell {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .tx-btn {
        padding: 0;
        min-width: 0;
        height: auto;
        line-height: normal;
      }

      .replay-action {
        margin-top: 24px;
        display: flex;
        justify-content: center;
      }

      .empty-result-card {
        border: 1px dashed var(--mc-border) !important;
        min-height: 300px;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .empty-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        color: var(--mc-text-secondary);
        padding: 40px;
      }
      .placeholder-icon {
        font-size: 56px;
        width: 56px;
        height: 56px;
        color: var(--mc-text-muted);
        margin-bottom: 16px;
      }

      .rapid-fire-card {
        margin-top: 20px;
      }
      .rf-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.8125rem;
      }
      .rf-table th,
      .rf-table td {
        padding: 10px 14px;
        border-bottom: 1px solid var(--mc-border-light);
        text-align: left;
      }
      .font-xs {
        font-size: 0.75rem;
      }
      .error-text {
        color: var(--mc-danger);
        font-weight: 600;
      }
      .error-detail {
        font-size: 0.75rem;
        color: var(--mc-danger);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentSimulatorComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly cardsApi = inject(CardsApiService);
  private readonly txApi = inject(TransactionsApiService);
  private readonly refDataService = inject(ReferenceDataService);
  private readonly notify = inject(NotificationService);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly activeCards = signal<CardResponse[]>([]);
  readonly selectedCard = signal<CardResponse | null>(null);
  readonly currencies = signal<string[]>(['NPR', 'USD', 'EUR']);
  readonly mccOptions = signal<{ code: string; label: string }[]>([]);

  readonly currentKey = signal<string>(generateUuid());
  readonly lastSubmittedKey = signal<string | null>(null);
  readonly lastSubmittedRequest = signal<AuthorizeRequest | null>(null);

  readonly submitting = signal<boolean>(false);
  readonly result = signal<AuthorizeResult | null>(null);

  readonly rapidFiring = signal<boolean>(false);
  readonly rapidFireCurrent = signal<number>(0);
  readonly rapidFireResults = signal<RapidFireItem[]>([]);

  readonly form: FormGroup = this.fb.group({
    cardId: ['', [Validators.required]],
    amount: ['500.00', [Validators.required]],
    currency: ['NPR', [Validators.required]],
    merchantName: ['Bhatbhateni Supermarket', [Validators.required]],
    mcc: ['5411', [Validators.required]],
    reference: ['SIM-POS-1'],
  });

  ngOnInit(): void {
    this.loadCards();
    this.loadRefData();
  }

  loadCards(): void {
    this.cardsApi
      .listCards({ status: 'ACTIVE', size: 100 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          const content = res.content || [];
          this.activeCards.set(content);
          if (content.length > 0 && !this.form.value.cardId) {
            this.form.patchValue({ cardId: content[0].id });
            this.onCardSelected(content[0].id || '');
          }
        },
        error: () => {
          this.notify.error('Failed to load active cards');
        },
      });
  }

  loadRefData(): void {
    this.refDataService
      .getReferenceData()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          if (data.currencies?.length) {
            this.currencies.set(data.currencies);
          }
          if (data.mccs) {
            const opts = Object.entries(data.mccs).map(([code, label]) => ({
              code,
              label,
            }));
            this.mccOptions.set(opts);
          }
        },
      });
  }

  onCardSelected(cardId: string): void {
    const card = this.activeCards().find((c) => c.id === cardId) || null;
    this.selectedCard.set(card);
    if (card?.currency) {
      this.form.patchValue({ currency: card.currency });
    }
  }

  generateNewKey(): void {
    this.currentKey.set(generateUuid());
  }

  applyPreset(preset: 'grocery' | 'betting'): void {
    if (preset === 'grocery') {
      this.form.patchValue({
        amount: '500.00',
        merchantName: 'Bhatbhateni Supermarket',
        mcc: '5411',
        reference: `SIM-GROCERY-${Date.now().toString().slice(-4)}`,
      });
    } else if (preset === 'betting') {
      this.form.patchValue({
        amount: '100.00',
        merchantName: 'Royal Casino Nepal',
        mcc: '7995',
        reference: `SIM-BET-${Date.now().toString().slice(-4)}`,
      });
    }
    this.generateNewKey();
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }

    const val = this.form.value;
    let amountMinor: number;
    try {
      amountMinor = parseMajorToMinor(val.amount);
    } catch {
      this.notify.error('Invalid amount format');
      return;
    }

    const req: AuthorizeRequest = {
      cardId: val.cardId,
      amountMinor,
      currency: val.currency,
      merchantName: val.merchantName,
      mcc: val.mcc,
      reference: val.reference || undefined,
    };

    const key = this.currentKey();
    this.submitting.set(true);

    this.txApi
      .authorize(req, key)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          this.result.set(res);
          this.lastSubmittedKey.set(key);
          this.lastSubmittedRequest.set(req);
          if (res.replayed) {
            this.notify.info('Idempotent replay detected (cached response returned)');
          } else if (res.body.status === 'APPROVED') {
            this.notify.success('Transaction APPROVED');
          } else {
            this.notify.warning(`Transaction DECLINED: ${res.body.responseMessage}`);
          }
        },
        error: (err) => {
          this.submitting.set(false);
          this.notify.error(err?.detail || 'Authorization request failed');
        },
      });
  }

  replayRequest(): void {
    const key = this.lastSubmittedKey();
    const req = this.lastSubmittedRequest();
    if (!key || !req || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.txApi
      .authorize(req, key)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          this.result.set(res);
          if (res.replayed) {
            this.notify.info('Idempotent replay verified!');
          }
        },
        error: (err) => {
          this.submitting.set(false);
          this.notify.error(err?.detail || 'Replay request failed');
        },
      });
  }

  triggerRapidFire(): void {
    const cardId = this.form.value.cardId;
    if (!cardId || this.rapidFiring()) {
      return;
    }

    const val = this.form.value;
    let amountMinor: number;
    try {
      amountMinor = parseMajorToMinor(val.amount);
    } catch {
      this.notify.error('Invalid amount format');
      return;
    }

    const baseReq: AuthorizeRequest = {
      cardId,
      amountMinor,
      currency: val.currency,
      merchantName: val.merchantName,
      mcc: val.mcc,
      reference: 'SIM-RAPID',
    };

    const total = 6;
    const items: RapidFireItem[] = Array.from({ length: total }, (_, i) => ({
      index: i + 1,
      idempotencyKey: generateUuid(),
    }));

    this.rapidFireResults.set(items);
    this.rapidFiring.set(true);
    this.rapidFireCurrent.set(1);

    this.fireSequential(baseReq, items, 0);
  }

  private fireSequential(baseReq: AuthorizeRequest, items: RapidFireItem[], index: number): void {
    if (index >= items.length) {
      this.rapidFiring.set(false);
      this.notify.info('Rapid-fire test completed');
      return;
    }

    const currentItem = items[index];
    this.rapidFireCurrent.set(index + 1);

    this.txApi.authorize(baseReq, currentItem.idempotencyKey).subscribe({
      next: (res) => {
        const updated = [...this.rapidFireResults()];
        updated[index] = {
          ...currentItem,
          status: res.body.status,
          responseCode: res.body.responseCode,
          transactionId: res.body.transactionId,
          replayed: res.replayed,
        };
        this.rapidFireResults.set(updated);
        // Fire next immediately
        this.fireSequential(baseReq, items, index + 1);
      },
      error: (err) => {
        const updated = [...this.rapidFireResults()];
        updated[index] = {
          ...currentItem,
          error: err?.detail || 'Request failed',
        };
        this.rapidFireResults.set(updated);
        // Continue to next even on failure
        this.fireSequential(baseReq, items, index + 1);
      },
    });
  }

  viewTransaction(transactionId: string): void {
    this.dialog.open(TransactionDetailDialogComponent, {
      data: { transactionId },
      width: '680px',
      maxHeight: '90vh',
    });
  }
}
