import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../../core/config/app-config';
import { NotificationService } from '../../../core/notify/notification.service';
import { ReferenceDataService } from '../../../core/reference/reference-data.service';
import { CardsApiService } from '../../cards/cards-api.service';
import { TransactionsApiService } from '../transactions-api.service';
import { PaymentSimulatorComponent } from './payment-simulator.component';

describe('PaymentSimulatorComponent', () => {
  let component: PaymentSimulatorComponent;
  let fixture: ComponentFixture<PaymentSimulatorComponent>;
  let cardsApiSpy: jasmine.SpyObj<CardsApiService>;
  let txApiSpy: jasmine.SpyObj<TransactionsApiService>;
  let refDataSpy: jasmine.SpyObj<ReferenceDataService>;
  let notifySpy: jasmine.SpyObj<NotificationService>;
  let dialogSpy: jasmine.SpyObj<MatDialog>;

  const mockCard = {
    id: 'card-1',
    holderName: 'John Doe',
    maskedPan: '980400******1234',
    currency: 'NPR',
    balanceMinor: 100000,
    status: 'ACTIVE' as const,
  };

  beforeEach(async () => {
    cardsApiSpy = jasmine.createSpyObj('CardsApiService', ['listCards']);
    txApiSpy = jasmine.createSpyObj('TransactionsApiService', ['authorize']);
    refDataSpy = jasmine.createSpyObj('ReferenceDataService', ['getReferenceData']);
    notifySpy = jasmine.createSpyObj('NotificationService', [
      'success',
      'error',
      'info',
      'warning',
    ]);
    dialogSpy = jasmine.createSpyObj('MatDialog', ['open']);

    cardsApiSpy.listCards.and.returnValue(
      of({
        content: [mockCard],
        page: {
          number: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
        },
      }),
    );

    refDataSpy.getReferenceData.and.returnValue(
      of({
        currencies: ['NPR', 'USD'],
        mccs: { '5411': 'Grocery Stores', '7995': 'Betting' },
        responseCodes: { '00': 'Approved' },
        declineReasons: {},
        disputeReasons: [],
      }),
    );

    await TestBed.configureTestingModule({
      imports: [PaymentSimulatorComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
        { provide: CardsApiService, useValue: cardsApiSpy },
        { provide: TransactionsApiService, useValue: txApiSpy },
        { provide: ReferenceDataService, useValue: refDataSpy },
        { provide: NotificationService, useValue: notifySpy },
        { provide: MatDialog, useValue: dialogSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentSimulatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize and load active cards and ref data', () => {
    expect(component).toBeTruthy();
    expect(cardsApiSpy.listCards).toHaveBeenCalledWith({ status: 'ACTIVE', size: 100 });
    expect(component.activeCards().length).toBe(1);
    expect(component.form.value.cardId).toBe('card-1');
    expect(component.selectedCard()?.id).toBe('card-1');
  });

  it('should apply grocery preset and update form values', () => {
    const oldKey = component.currentKey();
    component.applyPreset('grocery');

    expect(component.form.value.amount).toBe('500.00');
    expect(component.form.value.mcc).toBe('5411');
    expect(component.form.value.merchantName).toBe('Bhatbhateni Supermarket');
    expect(component.currentKey()).not.toBe(oldKey);
  });

  it('should apply betting preset and update form values', () => {
    component.applyPreset('betting');

    expect(component.form.value.amount).toBe('100.00');
    expect(component.form.value.mcc).toBe('7995');
    expect(component.form.value.merchantName).toBe('Royal Casino Nepal');
  });

  it('should submit authorization request and show approved result', () => {
    txApiSpy.authorize.and.returnValue(
      of({
        body: {
          transactionId: 'tx-sim-1',
          status: 'APPROVED',
          responseCode: '00',
          responseMessage: 'Approved',
          authorizedAt: '2026-10-21T07:00:00Z',
        },
        replayed: false,
        idempotencyKey: component.currentKey(),
      }),
    );

    component.submit();

    expect(txApiSpy.authorize).toHaveBeenCalled();
    expect(component.submitting()).toBeFalse();
    expect(component.result()?.body.status).toBe('APPROVED');
    expect(notifySpy.success).toHaveBeenCalledWith('Transaction APPROVED');
  });

  it('should replay last submitted request with same key', () => {
    const key = component.currentKey();
    txApiSpy.authorize.and.returnValue(
      of({
        body: {
          transactionId: 'tx-sim-1',
          status: 'APPROVED',
          responseCode: '00',
          responseMessage: 'Approved',
        },
        replayed: false,
        idempotencyKey: key,
      }),
    );

    component.submit();

    txApiSpy.authorize.and.returnValue(
      of({
        body: {
          transactionId: 'tx-sim-1',
          status: 'APPROVED',
          responseCode: '00',
          responseMessage: 'Approved',
        },
        replayed: true,
        idempotencyKey: key,
      }),
    );

    component.replayRequest();

    expect(component.result()?.replayed).toBeTrue();
    expect(notifySpy.info).toHaveBeenCalledWith('Idempotent replay verified!');
  });

  it('should handle authorization error gracefully', () => {
    txApiSpy.authorize.and.returnValue(throwError(() => ({ detail: 'Card limit exceeded' })));

    component.submit();

    expect(component.submitting()).toBeFalse();
    expect(notifySpy.error).toHaveBeenCalledWith('Card limit exceeded');
  });

  it('should trigger rapid-fire test with sequential calls', () => {
    txApiSpy.authorize.and.returnValue(
      of({
        body: {
          transactionId: 'tx-seq',
          status: 'APPROVED',
          responseCode: '00',
          responseMessage: 'Approved',
        },
        replayed: false,
        idempotencyKey: 'key',
      }),
    );

    component.triggerRapidFire();

    expect(component.rapidFireResults().length).toBe(6);
    expect(txApiSpy.authorize).toHaveBeenCalledTimes(6);
    expect(notifySpy.info).toHaveBeenCalledWith('Rapid-fire test completed');
  });
});
