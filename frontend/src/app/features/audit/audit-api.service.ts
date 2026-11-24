import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client';
import { components } from '../../core/api/schema';

export type AuditEventResponse = components['schemas']['AuditEventResponse'];
export type PageResponseAuditEventResponse =
  components['schemas']['PageResponseAuditEventResponse'];

export interface AuditFilters {
  action?: string;
  entityType?: string;
  entityId?: string;
  actorLabel?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuditApiService {
  private readonly api = inject(ApiClient);

  listAuditEvents(filters: AuditFilters = {}): Observable<PageResponseAuditEventResponse> {
    const params: Record<string, string | number | undefined> = {};

    if (filters.action && filters.action.trim()) {
      params['action'] = filters.action.trim();
    }
    if (filters.entityType && filters.entityType.trim()) {
      params['entityType'] = filters.entityType.trim();
    }
    if (filters.entityId && filters.entityId.trim()) {
      params['entityId'] = filters.entityId.trim();
    }
    if (filters.actorLabel && filters.actorLabel.trim()) {
      params['actorLabel'] = filters.actorLabel.trim();
    }
    if (filters.from && filters.from.trim()) {
      params['from'] = filters.from.trim();
    }
    if (filters.to && filters.to.trim()) {
      params['to'] = filters.to.trim();
    }
    if (filters.page !== undefined) {
      params['page'] = filters.page;
    }
    if (filters.size !== undefined) {
      params['size'] = filters.size;
    }
    if (filters.sort && filters.sort.trim()) {
      params['sort'] = filters.sort.trim();
    }

    return this.api.get<PageResponseAuditEventResponse>('audit-events', params);
  }
}
