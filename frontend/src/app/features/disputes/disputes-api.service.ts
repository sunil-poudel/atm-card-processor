import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient, QueryParams } from '../../core/api/api-client';
import { components } from '../../core/api/schema';

export type DisputeResponse = components['schemas']['DisputeResponse'];
export type DisputeDetail = components['schemas']['DisputeDetail'];
export type DisputeEventResponse = components['schemas']['DisputeEventResponse'];
export type PageResponseDisputeResponse = components['schemas']['PageResponseDisputeResponse'];
export type OpenDisputeRequest = components['schemas']['OpenDisputeRequest'];
export type RequestEvidenceRequest = components['schemas']['RequestEvidenceRequest'];
export type SubmitEvidenceRequest = components['schemas']['SubmitEvidenceRequest'];
export type ResolveDisputeRequest = components['schemas']['ResolveDisputeRequest'];

@Injectable({
  providedIn: 'root',
})
export class DisputesApiService {
  private readonly api = inject(ApiClient);

  listDisputes(params?: QueryParams): Observable<PageResponseDisputeResponse> {
    return this.api.get<PageResponseDisputeResponse>('disputes', params);
  }

  getDispute(id: string): Observable<DisputeDetail> {
    return this.api.get<DisputeDetail>(`disputes/${id}`);
  }

  openDispute(req: OpenDisputeRequest): Observable<DisputeDetail> {
    return this.api.post<DisputeDetail>('disputes', req);
  }

  requestEvidence(id: string, req?: RequestEvidenceRequest): Observable<DisputeDetail> {
    return this.api.post<DisputeDetail>(`disputes/${id}/request-evidence`, req || {});
  }

  submitEvidence(id: string, req: SubmitEvidenceRequest): Observable<DisputeDetail> {
    return this.api.post<DisputeDetail>(`disputes/${id}/submit-evidence`, req);
  }

  resolveDispute(id: string, req: ResolveDisputeRequest): Observable<DisputeDetail> {
    return this.api.post<DisputeDetail>(`disputes/${id}/resolve`, req);
  }
}
