import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthStore } from '../../../core/auth/auth.store';
import { TransactionDetail, TransactionsApiService } from '../transactions-api.service';
import { TransactionDetailDialogComponent } from './transaction-detail-dialog.component';

describe('TransactionDetailDialogComponent', () => {
  let component: TransactionDetailDialogComponent;
  let fixture: ComponentFixture<TransactionDetailDialogComponent>;
  let txApiSpy: jasmine.SpyObj<TransactionsApiService>;
  let authStore: AuthStore;

  const mockTxDetail: TransactionDetail = {
    id: 'tx-123',
    createdAt: '2026-01-01T12:00:00Z',
    cardId: 'card-1',
    cardLast4: '1111',
    holderName: 'Alice Doe',
    merchantName: 'Supermarket',
    mcc: '5411',
    amountMinor: 250000,
    currency: 'NPR',
    status: 'APPROVED',
    responseCode: '00',
    flagged: false,
    riskScore: 25,
    source: 'USER',
    sourceName: 'Admin Web',
    reference: 'REF-001',
    balanceAfterMinor: 4750000,
    processingMillis: 42,
    idempotencyKey: 'idem-12345',
    ruleHits: [
      {
        ruleName: 'MAX_AMOUNT',
        ruleType: 'MAX_AMOUNT',
        points: 25,
        action: 'FLAG',
        detail: 'Amount above warning threshold',
      },
    ],
    dispute: undefined,
  };

  beforeEach(async () => {
    txApiSpy = jasmine.createSpyObj<TransactionsApiService>('TransactionsApiService', [
      'getTransaction',
    ]);
    txApiSpy.getTransaction.and.returnValue(of(mockTxDetail));

    await TestBed.configureTestingModule({
      imports: [TransactionDetailDialogComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: TransactionsApiService, useValue: txApiSpy },
        { provide: MatDialogRef, useValue: { close: jasmine.createSpy('close') } },
        {
          provide: MAT_DIALOG_DATA,
          useValue: { transactionId: 'tx-123' },
        },
      ],
    }).compileComponents();

    authStore = TestBed.inject(AuthStore);
    authStore.setSession('token', {
      id: 'officer-1',
      email: 'officer@minicard.local',
      displayName: 'Dispute Officer',
      role: 'DISPUTE_OFFICER',
    });

    fixture = TestBed.createComponent(TransactionDetailDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should load and render transaction details', () => {
    expect(txApiSpy.getTransaction).toHaveBeenCalledWith('tx-123');
    expect(component.tx()).toEqual(mockTxDetail);
  });

  it('should compute risk score styling class', () => {
    expect(component.getScoreClass(25)).toBe('score-low');
    expect(component.getScoreClass(60)).toBe('score-med');
    expect(component.getScoreClass(85)).toBe('score-high');
  });

  it('should allow opening dispute for DISPUTE_OFFICER when approved', () => {
    expect(component.canOpenDispute()).toBeTrue();
    const dialog = fixture.debugElement.injector.get(MatDialog);
    spyOn(dialog, 'open').and.returnValue({
      afterClosed: () => of({ id: 'dispute-1' }),
    } as unknown as ReturnType<typeof dialog.open>);

    component.openDispute(mockTxDetail);
    expect(dialog.open).toHaveBeenCalled();
  });
});
