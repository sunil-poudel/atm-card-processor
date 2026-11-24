import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client';
import { components } from '../../core/api/schema';

export type ApiClientResponse = components['schemas']['ApiClientResponse'];
export type CreateApiClientRequest = components['schemas']['CreateApiClientRequest'];
export type ApiClientCreatedResponse = components['schemas']['ApiClientCreatedResponse'];
export type PageResponseApiClientResponse = components['schemas']['PageResponseApiClientResponse'];

@Injectable({
  providedIn: 'root',
})
export class ApiClientsApiService {
  private readonly api = inject(ApiClient);

  listClients(
    page = 0,
    size = 20,
    sort = 'createdAt,desc',
  ): Observable<PageResponseApiClientResponse> {
    return this.api.get<PageResponseApiClientResponse>('api-clients', {
      page,
      size,
      sort,
    });
  }

  createClient(req: CreateApiClientRequest): Observable<ApiClientCreatedResponse> {
    return this.api.post<ApiClientCreatedResponse>('api-clients', req);
  }

  revokeClient(id: string): Observable<ApiClientResponse> {
    return this.api.post<ApiClientResponse>(`api-clients/${id}/revoke`);
  }
}
