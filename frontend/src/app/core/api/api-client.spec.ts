import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../config/app-config';
import { ApiClient } from './api-client';

describe('ApiClient', () => {
  let client: ApiClient;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ApiClient,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
      ],
    });

    client = TestBed.inject(ApiClient);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should make GET request with prepended apiBase', () => {
    client.get<{ id: string }>('cards/123').subscribe((res) => {
      expect(res.id).toBe('123');
    });

    const req = httpTesting.expectOne('/api/v1/cards/123');
    expect(req.request.method).toBe('GET');
    req.flush({ id: '123' });
  });

  it('should serialize query params correctly', () => {
    client.get('transactions', { page: 0, size: 20, status: 'APPROVED' }).subscribe();

    const req = httpTesting.expectOne('/api/v1/transactions?page=0&size=20&status=APPROVED');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should make POST request with body', () => {
    client.post('cards', { holderName: 'Alice' }).subscribe();

    const req = httpTesting.expectOne('/api/v1/cards');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ holderName: 'Alice' });
    req.flush({});
  });

  it('should support DELETE request', () => {
    client.delete('cards/123').subscribe();

    const req = httpTesting.expectOne('/api/v1/cards/123');
    expect(req.request.method).toBe('DELETE');
    req.flush({});
  });

  it('should support postWithResponse returning full HttpResponse', () => {
    client.postWithResponse<{ ok: boolean }>('authorize', { amount: 100 }).subscribe((res) => {
      expect(res.body?.ok).toBeTrue();
      expect(res.headers.get('Idempotent-Replayed')).toBe('true');
    });

    const req = httpTesting.expectOne('/api/v1/authorize');
    expect(req.request.method).toBe('POST');
    req.flush({ ok: true }, { headers: { 'Idempotent-Replayed': 'true' } });
  });

  it('should download blob response', () => {
    client.downloadBlob('transactions/export').subscribe((blob) => {
      expect(blob).toBeTruthy();
    });

    const req = httpTesting.expectOne('/api/v1/transactions/export');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['col1,col2\nval1,val2'], { type: 'text/csv' }));
  });
});
