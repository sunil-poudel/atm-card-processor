import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiClient } from '../../core/api/api-client';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import {
  DisputesApiService,
  OpenDisputeRequest,
  RequestEvidenceRequest,
  ResolveDisputeRequest,
  SubmitEvidenceRequest,
} from './disputes-api.service';

describe('DisputesApiService', () => {
  let service: DisputesApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DisputesApiService,
        ApiClient,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
      ],
    });

    service = TestBed.inject(DisputesApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should list disputes with query params', () => {
    service.listDisputes({ status: 'OPENED', page: 0, size: 20 }).subscribe();

    const req = httpTesting.expectOne('/api/v1/disputes?status=OPENED&page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [] });
  });

  it('should get dispute by id', () => {
    service.getDispute('d-1').subscribe((res) => {
      expect(res.id).toBe('d-1');
    });

    const req = httpTesting.expectOne('/api/v1/disputes/d-1');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'd-1' });
  });

  it('should open dispute', () => {
    const payload: OpenDisputeRequest = {
      transactionId: 'tx-1',
      reason: 'UNAUTHORIZED_TRANSACTION',
      description: 'Fraud suspected',
    };

    service.openDispute(payload).subscribe();

    const req = httpTesting.expectOne('/api/v1/disputes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'd-new' });
  });

  it('should request evidence', () => {
    const payload: RequestEvidenceRequest = { note: 'Please provide receipts' };

    service.requestEvidence('d-1', payload).subscribe();

    const req = httpTesting.expectOne('/api/v1/disputes/d-1/request-evidence');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'd-1', status: 'EVIDENCE_REQUESTED' });
  });

  it('should submit evidence', () => {
    const payload: SubmitEvidenceRequest = { note: 'Receipt uploaded by merchant' };

    service.submitEvidence('d-1', payload).subscribe();

    const req = httpTesting.expectOne('/api/v1/disputes/d-1/submit-evidence');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'd-1', status: 'UNDER_REVIEW' });
  });

  it('should resolve dispute', () => {
    const payload: ResolveDisputeRequest = { outcome: 'WON', note: 'Customer refunded' };

    service.resolveDispute('d-1', payload).subscribe();

    const req = httpTesting.expectOne('/api/v1/disputes/d-1/resolve');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'd-1', status: 'RESOLVED_WON' });
  });
});
