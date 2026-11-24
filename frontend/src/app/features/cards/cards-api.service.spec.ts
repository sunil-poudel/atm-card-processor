import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiClient } from '../../core/api/api-client';
import { APP_CONFIG, DEFAULT_APP_CONFIG } from '../../core/config/app-config';
import { CardsApiService, IssueCardRequest } from './cards-api.service';

describe('CardsApiService', () => {
  let service: CardsApiService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CardsApiService,
        ApiClient,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: APP_CONFIG, useValue: DEFAULT_APP_CONFIG },
      ],
    });

    service = TestBed.inject(CardsApiService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should list cards with query parameters', () => {
    service.listCards({ status: 'ACTIVE', page: 0, size: 20 }).subscribe();

    const req = httpTesting.expectOne('/api/v1/cards?status=ACTIVE&page=0&size=20');
    expect(req.request.method).toBe('GET');
    req.flush({ content: [] });
  });

  it('should get card by id', () => {
    service.getCard('card-123').subscribe();

    const req = httpTesting.expectOne('/api/v1/cards/card-123');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'card-123' });
  });

  it('should issue a new card', () => {
    const payload: IssueCardRequest = {
      holderName: 'Alice Doe',
      currency: 'NPR',
      validityYears: 3,
      perTransactionLimitMinor: 5000000,
      dailyLimitMinor: 10000000,
      initialBalanceMinor: 0,
      blockedMccs: [],
    };

    service.issueCard(payload).subscribe();

    const req = httpTesting.expectOne('/api/v1/cards');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 'new-card-123' });
  });

  it('should activate card', () => {
    service.activateCard('card-123').subscribe();

    const req = httpTesting.expectOne('/api/v1/cards/card-123/activate');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'card-123', status: 'ACTIVE' });
  });

  it('should block card with reason', () => {
    service.blockCard('card-123', 'Suspected fraud').subscribe();

    const req = httpTesting.expectOne('/api/v1/cards/card-123/block');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Suspected fraud' });
    req.flush({ id: 'card-123', status: 'BLOCKED' });
  });

  it('should unblock card', () => {
    service.unblockCard('card-123').subscribe();

    const req = httpTesting.expectOne('/api/v1/cards/card-123/unblock');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'card-123', status: 'ACTIVE' });
  });

  it('should close card', () => {
    service.closeCard('card-123').subscribe();

    const req = httpTesting.expectOne('/api/v1/cards/card-123/close');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'card-123', status: 'CLOSED' });
  });

  it('should top up card balance', () => {
    service.topUpCard('card-123', 50000, 'Top up test').subscribe();

    const req = httpTesting.expectOne('/api/v1/cards/card-123/topups');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amountMinor: 50000, description: 'Top up test' });
    req.flush({ id: 'ledger-1', amountMinor: 50000 });
  });

  it('should update card limits with version', () => {
    service.updateLimits('card-123', 60000, 120000, 1).subscribe();

    const req = httpTesting.expectOne('/api/v1/cards/card-123/limits');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      perTransactionLimitMinor: 60000,
      dailyLimitMinor: 120000,
      version: 1,
    });
    req.flush({ id: 'card-123' });
  });

  it('should update blocked MCCs', () => {
    service.updateBlockedMccs('card-123', ['7995', '6051'], 2).subscribe();

    const req = httpTesting.expectOne('/api/v1/cards/card-123/blocked-mccs');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      blockedMccs: ['7995', '6051'],
      version: 2,
    });
    req.flush({ id: 'card-123' });
  });
});
