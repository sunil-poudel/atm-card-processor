import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client';
import { components } from '../../core/api/schema';

export type FraudRuleResponse = components['schemas']['FraudRuleResponse'];
export type CreateFraudRuleRequest = components['schemas']['CreateFraudRuleRequest'];
export type UpdateFraudRuleRequest = components['schemas']['UpdateFraudRuleRequest'];

@Injectable({
  providedIn: 'root',
})
export class FraudRulesApiService {
  private readonly api = inject(ApiClient);

  listRules(): Observable<FraudRuleResponse[]> {
    return this.api.get<FraudRuleResponse[]>('fraud-rules');
  }

  getRule(id: string): Observable<FraudRuleResponse> {
    return this.api.get<FraudRuleResponse>(`fraud-rules/${id}`);
  }

  createRule(req: CreateFraudRuleRequest): Observable<FraudRuleResponse> {
    return this.api.post<FraudRuleResponse>('fraud-rules', req);
  }

  updateRule(id: string, req: UpdateFraudRuleRequest): Observable<FraudRuleResponse> {
    return this.api.put<FraudRuleResponse>(`fraud-rules/${id}`, req);
  }

  enableRule(id: string): Observable<FraudRuleResponse> {
    return this.api.post<FraudRuleResponse>(`fraud-rules/${id}/enable`);
  }

  disableRule(id: string): Observable<FraudRuleResponse> {
    return this.api.post<FraudRuleResponse>(`fraud-rules/${id}/disable`);
  }
}
