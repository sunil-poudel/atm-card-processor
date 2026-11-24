import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { ApiError } from '../../../core/api/api-error';
import { NotificationService } from '../../../core/notify/notification.service';
import { ReferenceDataService } from '../../../core/reference/reference-data.service';
import { applyServerErrors } from '../../../shared/forms/apply-server-errors';
import { FormErrorComponent } from '../../../shared/forms/form-error.component';
import { parseMajorToMinor } from '../../../shared/forms/money-parser';
import { CopyButtonComponent } from '../../../shared/ui/copy-button.component';
import { CardResponse, CardsApiService, IssueCardRequest } from '../cards-api.service';

@Component({
  selector: 'mc-card-issue-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    CopyButtonComponent,
    FormErrorComponent,
  ],
  template: `
    @if (!issuedCard()) {
      <h2 mat-dialog-title>Issue New Card</h2>
      <mat-dialog-content>
        <form [formGroup]="form" class="issue-form" id="issueCardForm" (ngSubmit)="onSubmit()">
          @if (generalError()) {
            <div class="alert alert-danger">{{ generalError() }}</div>
          }

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Cardholder Name</mat-label>
              <input
                matInput
                formControlName="holderName"
                placeholder="e.g. Jane Doe"
                maxlength="100"
              />
              <mc-form-error
                [control]="form.controls.holderName"
                fieldName="Cardholder Name"
              ></mc-form-error>
            </mat-form-field>
          </div>

          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Currency</mat-label>
              <mat-select formControlName="currency">
                @for (c of currencies(); track c) {
                  <mat-option [value]="c">{{ c }}</mat-option>
                }
              </mat-select>
              <mc-form-error
                [control]="form.controls.currency"
                fieldName="Currency"
              ></mc-form-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Validity (Years)</mat-label>
              <mat-select formControlName="validityYears">
                <mat-option [value]="1">1 Year</mat-option>
                <mat-option [value]="2">2 Years</mat-option>
                <mat-option [value]="3">3 Years</mat-option>
                <mat-option [value]="4">4 Years</mat-option>
                <mat-option [value]="5">5 Years</mat-option>
              </mat-select>
              <mc-form-error
                [control]="form.controls.validityYears"
                fieldName="Validity Years"
              ></mc-form-error>
            </mat-form-field>
          </div>

          <div class="form-row two-col">
            <mat-form-field appearance="outline">
              <mat-label>Per-Transaction Limit</mat-label>
              <input matInput formControlName="perTransactionLimit" placeholder="0.00" />
              <span matTextPrefix class="prefix-currency"
                >{{ form.controls.currency.value }}&nbsp;</span
              >
              <mc-form-error
                [control]="form.controls.perTransactionLimit"
                fieldName="Per-Transaction Limit"
              ></mc-form-error>
            </mat-form-field>

            <mat-form-field appearance="outline">
              <mat-label>Daily Limit</mat-label>
              <input matInput formControlName="dailyLimit" placeholder="0.00" />
              <span matTextPrefix class="prefix-currency"
                >{{ form.controls.currency.value }}&nbsp;</span
              >
              <mc-form-error
                [control]="form.controls.dailyLimit"
                fieldName="Daily Limit"
              ></mc-form-error>
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Initial Balance (Optional)</mat-label>
              <input matInput formControlName="initialBalance" placeholder="0.00" />
              <span matTextPrefix class="prefix-currency"
                >{{ form.controls.currency.value }}&nbsp;</span
              >
              <mat-hint>Amount to fund onto the card at issuance</mat-hint>
              <mc-form-error
                [control]="form.controls.initialBalance"
                fieldName="Initial Balance"
              ></mc-form-error>
            </mat-form-field>
          </div>

          <div class="form-row">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Blocked MCCs</mat-label>
              <mat-select formControlName="blockedMccs" multiple>
                @for (entry of mccsList(); track entry.code) {
                  <mat-option [value]="entry.code">
                    {{ entry.code }} ({{ entry.description }})
                  </mat-option>
                }
              </mat-select>
              <mat-hint
                >Transactions from selected Merchant Category Codes will be declined</mat-hint
              >
            </mat-form-field>
          </div>
        </form>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button mat-dialog-close [disabled]="loading()">Cancel</button>
        <button
          mat-flat-button
          color="primary"
          type="submit"
          form="issueCardForm"
          [disabled]="form.invalid || loading()"
        >
          @if (loading()) {
            <mat-spinner diameter="18"></mat-spinner>
            <span>Issuing...</span>
          } @else {
            <span>Issue Card</span>
          }
        </button>
      </mat-dialog-actions>
    } @else {
      <!-- One-time reveal panel -->
      <h2 mat-dialog-title class="reveal-title">
        <mat-icon color="warn">warning</mat-icon>
        Card Issued Successfully
      </h2>
      <mat-dialog-content>
        <div class="reveal-banner">
          <strong>Shown once. Copy it now.</strong>
          <p>
            For PCI-DSS compliance, full card numbers are never stored in plain text and cannot be
            retrieved again after leaving this screen.
          </p>
        </div>

        <div class="pan-reveal-box">
          <span class="pan-digits">{{ issuedCard()?.pan }}</span>
          <mc-copy-button [value]="issuedCard()?.pan || ''"></mc-copy-button>
        </div>

        <div class="ack-container">
          <mat-checkbox (change)="panAcknowledged.set($event.checked)">
            I have recorded the full card number securely.
          </mat-checkbox>
        </div>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button
          mat-flat-button
          color="primary"
          [disabled]="!panAcknowledged()"
          (click)="onFinishedReveal()"
        >
          Proceed to Card Details
        </button>
      </mat-dialog-actions>
    }
  `,
  styles: [
    `
      .issue-form {
        display: flex;
        flex-direction: column;
        gap: 12px;
        padding-top: 8px;
        min-width: 480px;
      }
      .two-col {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
      }
      .full-width {
        width: 100%;
      }
      .prefix-currency {
        color: #64748b;
        font-weight: 500;
        font-size: 13px;
      }
      .alert-danger {
        background-color: #fee2e2;
        color: #b91c1c;
        border: 1px solid #fca5a5;
        padding: 10px;
        border-radius: 6px;
        font-size: 13px;
        margin-bottom: 8px;
      }
      .reveal-title {
        display: flex;
        align-items: center;
        gap: 8px;
        color: #0f172a;
      }
      .reveal-banner {
        background: #fffbeb;
        border: 1px solid #fde68a;
        color: #92400e;
        padding: 12px;
        border-radius: 8px;
        font-size: 13px;
        margin-bottom: 20px;
      }
      .pan-reveal-box {
        display: flex;
        align-items: center;
        justify-content: space-between;
        background: #f1f5f9;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        padding: 16px 20px;
        margin-bottom: 20px;
      }
      .pan-digits {
        font-family: var(--mc-font-mono);
        font-size: 20px;
        font-weight: 700;
        letter-spacing: 2px;
        color: #0f172a;
      }
      .ack-container {
        padding: 8px 0;
      }
      @media (max-width: 600px) {
        .issue-form {
          min-width: 100%;
        }
        .two-col {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CardIssueDialogComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly cardsApi = inject(CardsApiService);
  private readonly refDataService = inject(ReferenceDataService);
  private readonly notify = inject(NotificationService);
  private readonly dialogRef = inject(MatDialogRef<CardIssueDialogComponent>);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly generalError = signal<string | null>(null);
  readonly issuedCard = signal<CardResponse | null>(null);
  readonly panAcknowledged = signal(false);

  readonly currencies = signal<string[]>(['NPR', 'USD']);
  readonly mccsList = signal<{ code: string; description: string }[]>([]);

  readonly form = this.fb.nonNullable.group({
    holderName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    currency: ['NPR', [Validators.required]],
    validityYears: [3, [Validators.required, Validators.min(1), Validators.max(5)]],
    perTransactionLimit: ['50000.00', [Validators.required]],
    dailyLimit: ['100000.00', [Validators.required]],
    initialBalance: ['0.00'],
    blockedMccs: [[] as string[]],
  });

  ngOnInit(): void {
    this.refDataService.getReferenceData().subscribe({
      next: (data) => {
        if (data.currencies?.length) {
          this.currencies.set(data.currencies);
        }
        if (data.mccs) {
          const mccs = Object.entries(data.mccs).map(([code, description]) => ({
            code,
            description,
          }));
          this.mccsList.set(mccs);
        }
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.loading()) {
      return;
    }

    this.generalError.set(null);
    let perTxLimitMinor: number;
    let dailyLimitMinor: number;
    let initialBalanceMinor = 0;

    try {
      perTxLimitMinor = parseMajorToMinor(this.form.controls.perTransactionLimit.value);
    } catch {
      this.form.controls.perTransactionLimit.setErrors({ serverError: 'Invalid numeric amount' });
      return;
    }

    try {
      dailyLimitMinor = parseMajorToMinor(this.form.controls.dailyLimit.value);
    } catch {
      this.form.controls.dailyLimit.setErrors({ serverError: 'Invalid numeric amount' });
      return;
    }

    if (this.form.controls.initialBalance.value) {
      try {
        initialBalanceMinor = parseMajorToMinor(this.form.controls.initialBalance.value);
      } catch {
        this.form.controls.initialBalance.setErrors({ serverError: 'Invalid numeric amount' });
        return;
      }
    }

    if (dailyLimitMinor < perTxLimitMinor) {
      this.form.controls.dailyLimit.setErrors({
        serverError: 'Daily limit must be greater than or equal to per-transaction limit',
      });
      return;
    }

    const payload: IssueCardRequest = {
      holderName: this.form.controls.holderName.value.trim(),
      currency: this.form.controls.currency.value,
      validityYears: this.form.controls.validityYears.value,
      perTransactionLimitMinor: perTxLimitMinor,
      dailyLimitMinor: dailyLimitMinor,
      initialBalanceMinor: initialBalanceMinor,
      blockedMccs: this.form.controls.blockedMccs.value,
    };

    this.loading.set(true);

    this.cardsApi.issueCard(payload).subscribe({
      next: (res) => {
        this.loading.set(false);
        this.notify.success('Card issued successfully');
        if (res.pan) {
          this.issuedCard.set(res);
        } else {
          this.dialogRef.close(res);
          this.router.navigate(['/cards', res.id]);
        }
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (err instanceof ApiError) {
          applyServerErrors(this.form, err);
          if (err.detail) {
            this.generalError.set(err.detail);
          }
        } else {
          this.generalError.set('Failed to issue card. Please try again.');
        }
      },
    });
  }

  onFinishedReveal(): void {
    const card = this.issuedCard();
    this.dialogRef.close(card);
    if (card?.id) {
      this.router.navigate(['/cards', card.id]);
    }
  }
}
