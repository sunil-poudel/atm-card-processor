import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthStore } from '../../core/auth/auth.store';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import { NotificationService } from '../../core/notify/notification.service';
import {
  ReferenceDataResponse,
  ReferenceDataService,
} from '../../core/reference/reference-data.service';
import {
  PageResponseTransactionSummary,
  TransactionsApiService,
  TransactionSummary,
} from './transactions-api.service';
import { TransactionsListComponent } from './transactions-list.component';

describe('TransactionsListComponent', () => {
  let component: TransactionsListComponent;
  let fixture: ComponentFixture<TransactionsListComponent>;
  let txApiSpy: jasmine.SpyObj<TransactionsApiService>;
  let refDataSpy: jasmine.SpyObj<ReferenceDataService>;
  let authStore: AuthStore;
  let router: Router;

  const mockTx: TransactionSummary = {
    id: 'tx-1',
    createdAt: '2026-01-01T12:00:00Z',
    cardLast4: '1111',
    holderName: 'Alice Doe',
    merchantName: 'Supermarket',
    mcc: '5411',
    amountMinor: 250000,
    currency: 'NPR',
    status: 'APPROVED',
    responseCode: '00',
    flagged: false,
    riskScore: 10,
    source: 'USER',
  };

  const mockPageResponse: PageResponseTransactionSummary = {
    content: [mockTx],
    page: {
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    },
  };

  const mockRefData: ReferenceDataResponse = {
    currencies: ['NPR', 'USD'],
    mccs: { '5411': 'Grocery' },
    responseCodes: { '00': 'Approved' },
    declineReasons: { INSUFFICIENT_FUNDS: 'Insufficient funds' },
    disputeReasons: ['FRAUD'],
  };

  beforeEach(async () => {
    txApiSpy = jasmine.createSpyObj<TransactionsApiService>('TransactionsApiService', [
      'listTransactions',
      'exportCsv',
    ]);
    refDataSpy = jasmine.createSpyObj<ReferenceDataService>('ReferenceDataService', [
      'getReferenceData',
    ]);

    txApiSpy.listTransactions.and.returnValue(of(mockPageResponse));
    refDataSpy.getReferenceData.and.returnValue(of(mockRefData));

    await TestBed.configureTestingModule({
      imports: [TransactionsListComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
        AuthStore,
        { provide: TransactionsApiService, useValue: txApiSpy },
        { provide: ReferenceDataService, useValue: refDataSpy },
        {
          provide: NotificationService,
          useValue: jasmine.createSpyObj('NotificationService', ['success', 'error']),
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (_k: string) => null,
              },
            },
          },
        },
      ],
    }).compileComponents();

    authStore = TestBed.inject(AuthStore);
    authStore.setSession('token', {
      id: 'admin-1',
      email: 'admin@minicard.local',
      displayName: 'Admin User',
      role: 'ADMIN',
    });

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(TransactionsListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should load transactions on init', () => {
    expect(txApiSpy.listTransactions).toHaveBeenCalled();
    expect(component.transactions().length).toBe(1);
    expect(component.transactions()[0].merchantName).toBe('Supermarket');
  });

  it('should open transaction detail dialog on row click', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    spyOn(dialog, 'open');

    component.onRowClick(mockTx);
    expect(dialog.open).toHaveBeenCalled();
  });

  it('should clear filters and reset', () => {
    component.filterForm.patchValue({ q: 'Grocery', status: 'APPROVED' });
    expect(component.isDefaultFilters()).toBeFalse();

    component.clearFilters();
    expect(component.filterForm.value.q).toBe('');
    expect(component.filterForm.value.status).toBe('');
    expect(component.isDefaultFilters()).toBeTrue();
  });

  it('should export CSV on exportCsv action', () => {
    txApiSpy.exportCsv.and.returnValue(of(new Blob(['csv,content'], { type: 'text/csv' })));
    spyOn(window.URL, 'createObjectURL').and.returnValue('blob:test');
    spyOn(window.URL, 'revokeObjectURL');

    component.exportCsv();

    expect(txApiSpy.exportCsv).toHaveBeenCalled();
  });
});
