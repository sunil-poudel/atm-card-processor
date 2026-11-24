import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiClient } from '../../core/api/api-client';
import { AuditApiService } from './audit-api.service';

describe('AuditApiService', () => {
  let service: AuditApiService;
  let apiClientSpy: jasmine.SpyObj<ApiClient>;

  beforeEach(() => {
    apiClientSpy = jasmine.createSpyObj<ApiClient>('ApiClient', ['get']);

    TestBed.configureTestingModule({
      providers: [AuditApiService, { provide: ApiClient, useValue: apiClientSpy }],
    });

    service = TestBed.inject(AuditApiService);
  });

  it('should call audit-events endpoint with query parameters', () => {
    const mockResponse = {
      content: [
        {
          id: 'ae1',
          action: 'CARD_ISSUED',
          actorLabel: 'admin@minicard.local',
          occurredAt: '2026-01-01T00:00:00Z',
        },
      ],
      page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
    };
    apiClientSpy.get.and.returnValue(of(mockResponse));

    let result: unknown;
    service
      .listAuditEvents({
        action: 'CARD_ISSUED',
        actorLabel: 'admin@minicard.local',
        page: 0,
        size: 20,
        sort: 'occurredAt,desc',
      })
      .subscribe((r) => (result = r));

    expect(apiClientSpy.get).toHaveBeenCalledWith('audit-events', {
      action: 'CARD_ISSUED',
      actorLabel: 'admin@minicard.local',
      page: 0,
      size: 20,
      sort: 'occurredAt,desc',
    });
    expect(result).toEqual(mockResponse);
  });
});
