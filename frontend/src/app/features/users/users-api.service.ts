import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client';
import { components } from '../../core/api/schema';

export type UserResponse = components['schemas']['UserResponse'];
export type CreateUserRequest = components['schemas']['CreateUserRequest'];
export type UpdateUserRequest = components['schemas']['UpdateUserRequest'];
export type PageResponseUserResponse = components['schemas']['PageResponseUserResponse'];

@Injectable({
  providedIn: 'root',
})
export class UsersApiService {
  private readonly api = inject(ApiClient);

  listUsers(page = 0, size = 20, sort = 'email,asc'): Observable<PageResponseUserResponse> {
    return this.api.get<PageResponseUserResponse>('users', {
      page,
      size,
      sort,
    });
  }

  getUser(id: string): Observable<UserResponse> {
    return this.api.get<UserResponse>(`users/${id}`);
  }

  createUser(req: CreateUserRequest): Observable<UserResponse> {
    return this.api.post<UserResponse>('users', req);
  }

  updateUser(id: string, req: UpdateUserRequest): Observable<UserResponse> {
    return this.api.patch<UserResponse>(`users/${id}`, req);
  }
}
