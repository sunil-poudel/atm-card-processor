import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ApiClient } from '../../core/api/api-client';
import { UsersApiService } from './users-api.service';

describe('UsersApiService', () => {
  let service: UsersApiService;
  let apiClientSpy: jasmine.SpyObj<ApiClient>;

  beforeEach(() => {
    apiClientSpy = jasmine.createSpyObj<ApiClient>('ApiClient', ['get', 'post', 'patch']);

    TestBed.configureTestingModule({
      providers: [UsersApiService, { provide: ApiClient, useValue: apiClientSpy }],
    });

    service = TestBed.inject(UsersApiService);
  });

  it('should list users with pagination and sort params', () => {
    const mockResponse = {
      content: [{ id: 'u1', email: 'admin@minicard.local', role: 'ADMIN' as const }],
      page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
    };
    apiClientSpy.get.and.returnValue(of(mockResponse));

    let result: unknown;
    service.listUsers(0, 20, 'email,asc').subscribe((r) => (result = r));

    expect(apiClientSpy.get).toHaveBeenCalledWith('users', {
      page: 0,
      size: 20,
      sort: 'email,asc',
    });
    expect(result).toEqual(mockResponse);
  });

  it('should get a single user by id', () => {
    const mockUser = { id: 'u1', email: 'test@minicard.local' };
    apiClientSpy.get.and.returnValue(of(mockUser));

    let result: unknown;
    service.getUser('u1').subscribe((r) => (result = r));

    expect(apiClientSpy.get).toHaveBeenCalledWith('users/u1');
    expect(result).toEqual(mockUser);
  });

  it('should create a user', () => {
    const req = {
      email: 'new@minicard.local',
      displayName: 'New Staff',
      role: 'ANALYST' as const,
      password: 'StrongPassword123!',
    };
    const mockUser = { id: 'u2', ...req };
    apiClientSpy.post.and.returnValue(of(mockUser));

    let result: unknown;
    service.createUser(req).subscribe((r) => (result = r));

    expect(apiClientSpy.post).toHaveBeenCalledWith('users', req);
    expect(result).toEqual(mockUser);
  });

  it('should update a user', () => {
    const req = {
      displayName: 'Updated Name',
      role: 'DISPUTE_OFFICER' as const,
      enabled: true,
      version: 0,
    };
    const mockUser = { id: 'u2', ...req };
    apiClientSpy.patch.and.returnValue(of(mockUser));

    let result: unknown;
    service.updateUser('u2', req).subscribe((r) => (result = r));

    expect(apiClientSpy.patch).toHaveBeenCalledWith('users/u2', req);
    expect(result).toEqual(mockUser);
  });
});
