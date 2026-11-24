import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import {
  DailyReportResponse,
  ReportsApiService,
  SummaryReportResponse,
} from '../reports/reports-api.service';
import {
  PageResponseTransactionSummary,
  TransactionsApiService,
} from '../transactions/transactions-api.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let reportsApiSpy: jasmine.SpyObj<ReportsApiService>;
  let txApiSpy: jasmine.SpyObj<TransactionsApiService>;

  const mockDaily: DailyReportResponse = {
    date: '2026-10-21',
    attempts: 42,
    approved: 40,
    declined: 2,
    flagged: 1,
    approvalRatePercent: 95.24,
    approvedVolume: [{ currency: 'NPR', amountMinor: 1500000 }],
  };

  const mockSummary: SummaryReportResponse = {
    activeCards: 125,
    openDisputes: 3,
    series: [
      {
        date: '2026-10-20',
        approved: 28,
        declined: 2,
      },
      {
        date: '2026-10-21',
        approved: 40,
        declined: 2,
      },
    ],
  };

  const mockFlagged: PageResponseTransactionSummary = {
    content: [
      {
        id: 'tx-flagged-1',
        createdAt: '2026-10-21T07:15:00Z',
        cardLast4: '9999',
        merchantName: 'Casino Palace',
        mcc: '7995',
        amountMinor: 500000,
        currency: 'NPR',
        riskScore: 75,
        status: 'DECLINED',
        flagged: true,
      },
    ],
  };

  beforeEach(async () => {
    reportsApiSpy = jasmine.createSpyObj('ReportsApiService', [
      'getDailyReport',
      'getSummaryReport',
    ]);
    txApiSpy = jasmine.createSpyObj('TransactionsApiService', ['listTransactions']);

    reportsApiSpy.getDailyReport.and.returnValue(of(mockDaily));
    reportsApiSpy.getSummaryReport.and.returnValue(of(mockSummary));
    txApiSpy.listTransactions.and.returnValue(of(mockFlagged));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
        { provide: ReportsApiService, useValue: reportsApiSpy },
        { provide: TransactionsApiService, useValue: txApiSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize and load daily metrics, summary metrics, and flagged transactions', () => {
    expect(component).toBeTruthy();
    expect(reportsApiSpy.getDailyReport).toHaveBeenCalled();
    expect(reportsApiSpy.getSummaryReport).toHaveBeenCalled();
    expect(txApiSpy.listTransactions).toHaveBeenCalled();

    expect(component.activeCards()).toBe(125);
    expect(component.openDisputes()).toBe(3);
    expect(component.todayAttempts()).toBe(42);
    expect(component.todayApprovedCount()).toBe(40);
    expect(component.todayApprovalRate()).toBe(95.24);
    expect(component.flaggedTransactions().length).toBe(1);
  });

  it('should build 14-day trend chart data', () => {
    const chart = component.chartData();
    expect(chart).toBeTruthy();
    expect(chart?.labels?.length).toBe(2);
    expect(chart?.datasets[0].data).toEqual([30, 42]);
    expect(chart?.datasets[1].data).toEqual([28, 40]);
  });

  it('should open transaction detail dialog when clicking action icon', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const spy = spyOn(dialog, 'open').and.returnValue(
      {} as unknown as ReturnType<typeof dialog.open>,
    );

    component.openTxDetail('tx-flagged-1');
    expect(spy).toHaveBeenCalled();
  });

  it('should handle API errors gracefully', () => {
    reportsApiSpy.getDailyReport.and.returnValue(
      throwError(() => ({ detail: 'Failed to connect to report service' })),
    );

    component.loadData();
    expect(component.loading()).toBeFalse();
    expect(component.error()).toBe('Failed to connect to report service');
  });
});
