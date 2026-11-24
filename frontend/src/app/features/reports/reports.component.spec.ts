import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import { NotificationService } from '../../core/notify/notification.service';
import { ReferenceDataService } from '../../core/reference/reference-data.service';
import {
  DailyReportResponse,
  ReportsApiService,
  SummaryReportResponse,
} from './reports-api.service';
import { ReportsComponent } from './reports.component';

describe('ReportsComponent', () => {
  let component: ReportsComponent;
  let fixture: ComponentFixture<ReportsComponent>;
  let reportsApiSpy: jasmine.SpyObj<ReportsApiService>;
  let notifySpy: jasmine.SpyObj<NotificationService>;

  const mockDaily: DailyReportResponse = {
    date: '2026-10-21',
    attempts: 120,
    approved: 110,
    declined: 10,
    flagged: 4,
    approvalRatePercent: 91.67,
    approvedVolume: [{ currency: 'NPR', amountMinor: 25000000 }],
    declinesByCode: [{ responseCode: '51', message: 'Insufficient funds', count: 6 }],
    topMccs: [{ mcc: '5411', attempts: 45 }],
    hourly: [{ hour: 10, attempts: 25, approved: 23, declined: 2 }],
  };

  const mockSummary: SummaryReportResponse = {
    from: '2026-10-01',
    to: '2026-10-21',
    activeCards: 340,
    openDisputes: 5,
    totals: {
      attempts: 1500,
      approved: 1400,
      declined: 100,
      flagged: 25,
      approvalRatePercent: 93.33,
    },
    approvedVolume: [{ currency: 'NPR', amountMinor: 500000000 }],
    series: [
      {
        date: '2026-10-20',
        approved: 95,
        declined: 5,
        flagged: 1,
      },
      {
        date: '2026-10-21',
        approved: 110,
        declined: 10,
        flagged: 4,
      },
    ],
  };

  beforeEach(async () => {
    reportsApiSpy = jasmine.createSpyObj('ReportsApiService', [
      'getDailyReport',
      'getSummaryReport',
    ]);
    notifySpy = jasmine.createSpyObj('NotificationService', [
      'success',
      'error',
      'info',
      'warning',
    ]);

    reportsApiSpy.getDailyReport.and.returnValue(of(mockDaily));
    reportsApiSpy.getSummaryReport.and.returnValue(of(mockSummary));

    await TestBed.configureTestingModule({
      imports: [ReportsComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
        { provide: ReportsApiService, useValue: reportsApiSpy },
        { provide: NotificationService, useValue: notifySpy },
        {
          provide: ReferenceDataService,
          useValue: {
            getReferenceData: () =>
              of({
                currencies: ['NPR'],
                mccs: { '5411': 'Grocery Stores' },
                responseCodes: {},
                declineReasons: {},
                disputeReasons: [],
              }),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should initialize and load daily report and summary report', () => {
    expect(component).toBeTruthy();
    expect(reportsApiSpy.getDailyReport).toHaveBeenCalled();
    expect(reportsApiSpy.getSummaryReport).toHaveBeenCalled();
    expect(component.dailyReport()?.attempts).toBe(120);
    expect(component.summaryReport()?.activeCards).toBe(340);
  });

  it('should populate hourly chart data', () => {
    const chartData = component.hourlyChartData();
    expect(chartData).toBeTruthy();
    expect(chartData?.labels?.length).toBe(24);
    expect(chartData?.datasets[0].data[10]).toBe(25);
    expect(chartData?.datasets[1].data[10]).toBe(23);
  });

  it('should populate series chart data', () => {
    const seriesData = component.seriesChartData();
    expect(seriesData).toBeTruthy();
    expect(seriesData?.labels?.length).toBe(2);
    expect(seriesData?.datasets[0].data[1]).toBe(120);
  });

  it('should validate range from <= to and notify error if invalid', () => {
    component.rangeForm.patchValue({
      from: '2026-10-25',
      to: '2026-10-20',
    });

    component.loadRangeSummary();
    expect(notifySpy.error).toHaveBeenCalledWith(
      '"From" date must be earlier than or equal to "To" date',
    );
  });

  it('should validate max 92 days limit', () => {
    component.rangeForm.patchValue({
      from: '2026-01-01',
      to: '2026-06-01',
    });

    component.loadRangeSummary();
    expect(notifySpy.error).toHaveBeenCalledWith('Maximum range is 92 days (approx. 3 months)');
  });

  it('should handle daily report error gracefully', () => {
    reportsApiSpy.getDailyReport.and.returnValue(
      throwError(() => ({ detail: 'Failed to compute daily aggregates' })),
    );

    component.loadDailyReport();
    expect(component.dailyLoading()).toBeFalse();
    expect(component.dailyError()).toBe('Failed to compute daily aggregates');
  });
});
