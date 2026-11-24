import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiClient } from '../../core/api/api-client';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import {
  CreateFraudRuleRequest,
  FraudRulesApiService,
  UpdateFraudRuleRequest,
} from './fraud-rules-api.service';

describe('FraudRulesApiService', () => {
  let service: FraudRulesApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        FraudRulesApiService,
        ApiClient,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
      ],
    });

    service = TestBed.inject(FraudRulesApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should list all rules', () => {
    service.listRules().subscribe((res) => {
      expect(res.length).toBe(1);
    });

    const req = httpTesting.expectOne('/api/v1/fraud-rules');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'r1', name: 'Rule 1' }]);
  });

  it('should get rule by id', () => {
    service.getRule('r1').subscribe((res) => {
      expect(res.id).toBe('r1');
    });

    const req = httpTesting.expectOne('/api/v1/fraud-rules/r1');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'r1' });
  });

  it('should create a new fraud rule', () => {
    const payload: CreateFraudRuleRequest = {
      name: 'High Amount',
      type: 'MAX_AMOUNT',
      thresholdValue: 10000000,
      scorePoints: 30,
      action: 'FLAG',
    };

    service.createRule(payload).subscribe();

    const req = httpTesting.expectOne('/api/v1/fraud-rules');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'r2', ...payload });
  });

  it('should update an existing rule', () => {
    const payload: UpdateFraudRuleRequest = {
      name: 'Updated Rule',
      scorePoints: 40,
      action: 'DECLINE',
      version: 1,
    };

    service.updateRule('r1', payload).subscribe();

    const req = httpTesting.expectOne('/api/v1/fraud-rules/r1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'r1', ...payload });
  });

  it('should enable rule', () => {
    service.enableRule('r1').subscribe();

    const req = httpTesting.expectOne('/api/v1/fraud-rules/r1/enable');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'r1', enabled: true });
  });

  it('should disable rule', () => {
    service.disableRule('r1').subscribe();

    const req = httpTesting.expectOne('/api/v1/fraud-rules/r1/disable');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'r1', enabled: false });
  });
});
