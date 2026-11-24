import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiClient } from '../../core/api/api-client';
import { ApiClientsApiService } from './api-clients-api.service';

describe('ApiClientsApiService', () => {
  let service: ApiClientsApiService;
  let apiClientSpy: jasmine.SpyObj<ApiClient>;

  beforeEach(() => {
    apiClientSpy = jasmine.createSpyObj<ApiClient>('ApiClient', ['get', 'post']);

    TestBed.configureTestingModule({
      providers: [ApiClientsApiService, { provide: ApiClient, useValue: apiClientSpy }],
    });

    service = TestBed.inject(ApiClientsApiService);
  });

  it('should list clients with pagination and sort params', () => {
    const mockResponse = {
      content: [{ id: 'c1', name: 'Simulator Acquirer', status: 'ACTIVE' as const }],
      page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
    };
    apiClientSpy.get.and.returnValue(of(mockResponse));

    let result: unknown;
    service.listClients(0, 20, 'createdAt,desc').subscribe((r) => (result = r));

    expect(apiClientSpy.get).toHaveBeenCalledWith('api-clients', {
      page: 0,
      size: 20,
      sort: 'createdAt,desc',
    });
    expect(result).toEqual(mockResponse);
  });

  it('should create an api client and return created response with apiKey', () => {
    const req = { name: 'Acquirer Gateway' };
    const mockCreated = {
      id: 'c2',
      name: 'Acquirer Gateway',
      keyPrefix: 'mck_12345678',
      apiKey: 'mck_12345678_secretvalue12345',
      status: 'ACTIVE' as const,
    };
    apiClientSpy.post.and.returnValue(of(mockCreated));

    let result: unknown;
    service.createClient(req).subscribe((r) => (result = r));

    expect(apiClientSpy.post).toHaveBeenCalledWith('api-clients', req);
    expect(result).toEqual(mockCreated);
  });

  it('should revoke an api client', () => {
    const mockRevoked = { id: 'c1', name: 'Simulator', status: 'REVOKED' as const };
    apiClientSpy.post.and.returnValue(of(mockRevoked));

    let result: unknown;
    service.revokeClient('c1').subscribe((r) => (result = r));

    expect(apiClientSpy.post).toHaveBeenCalledWith('api-clients/c1/revoke');
    expect(result).toEqual(mockRevoked);
  });
});
