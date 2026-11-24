import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiClient } from '../../core/api/api-client';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import { ReportsApiService } from './reports-api.service';

describe('ReportsApiService', () => {
  let service: ReportsApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ReportsApiService,
        ApiClient,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
      ],
    });

    service = TestBed.inject(ReportsApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should fetch daily report with date parameter', () => {
    service.getDailyReport('2026-10-21').subscribe((res) => {
      expect(res.date).toBe('2026-10-21');
    });

    const req = httpTesting.expectOne('/api/v1/reports/daily?date=2026-10-21');
    expect(req.request.method).toBe('GET');
    req.flush({ date: '2026-10-21', attempts: 100 });
  });

  it('should fetch summary report with from and to date parameters', () => {
    service.getSummaryReport('2026-10-01', '2026-10-21').subscribe((res) => {
      expect(res.from).toBe('2026-10-01');
    });

    const req = httpTesting.expectOne('/api/v1/reports/summary?from=2026-10-01&to=2026-10-21');
    expect(req.request.method).toBe('GET');
    req.flush({ from: '2026-10-01', to: '2026-10-21', activeCards: 50 });
  });
});
