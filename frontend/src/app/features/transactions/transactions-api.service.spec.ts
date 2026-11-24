import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiClient } from '../../core/api/api-client';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import { OpenDisputeRequest, TransactionsApiService } from './transactions-api.service';

describe('TransactionsApiService', () => {
  let service: TransactionsApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TransactionsApiService,
        ApiClient,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
      ],
    });

    service = TestBed.inject(TransactionsApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should list transactions with query parameters', () => {
    service.listTransactions({ status: 'APPROVED', page: 0, size: 20 }).subscribe();

    const req = httpTesting.expectOne('/api/v1/transactions?status=APPROVED&page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [] });
  });

  it('should get transaction detail by id', () => {
    service.getTransaction('tx-123').subscribe();

    const req = httpTesting.expectOne('/api/v1/transactions/tx-123');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'tx-123' });
  });

  it('should request CSV export blob', () => {
    service.exportCsv({ status: 'APPROVED' }).subscribe((blob) => {
      expect(blob).toBeTruthy();
    });

    const req = httpTesting.expectOne('/api/v1/transactions/export.csv?status=APPROVED');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['col1,col2'], { type: 'text/csv' }));
  });

  it('should open dispute on a transaction', () => {
    const payload: OpenDisputeRequest = {
      transactionId: 'tx-123',
      reason: 'UNAUTHORIZED_TRANSACTION',
      description: 'Customer never authorized transaction',
    };

    service.openDispute(payload).subscribe();

    const req = httpTesting.expectOne('/api/v1/disputes');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'dispute-1' });
  });

  it('should authorize transaction with Idempotency-Key and detect replay', () => {
    service
      .authorize(
        {
          cardId: 'c1',
          amountMinor: 50000,
          currency: 'NPR',
          merchantName: 'Bhatbhateni',
          mcc: '5411',
        },
        'key-uuid-123',
      )
      .subscribe((res) => {
        expect(res.body.status).toBe('APPROVED');
        expect(res.replayed).toBeTrue();
        expect(res.idempotencyKey).toBe('key-uuid-123');
      });

    const req = httpTesting.expectOne('/api/v1/authorize');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-uuid-123');
    req.flush(
      { transactionId: 'tx-1', status: 'APPROVED' },
      { headers: { 'Idempotent-Replayed': 'true' } },
    );
  });
});
