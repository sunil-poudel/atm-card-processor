import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { ApiClient, QueryParams } from '../../core/api/api-client';
import { components } from '../../core/api/schema';

export type TransactionSummary = components['schemas']['TransactionSummary'];
export type TransactionDetail = components['schemas']['TransactionDetail'];
export type PageResponseTransactionSummary =
  components['schemas']['PageResponseTransactionSummary'];
export type OpenDisputeRequest = components['schemas']['OpenDisputeRequest'];
export type DisputeDetail = components['schemas']['DisputeDetail'];
export type AuthorizeRequest = components['schemas']['AuthorizeRequest'];
export type AuthorizeResponse = components['schemas']['AuthorizeResponse'];

export interface AuthorizeResult {
  body: AuthorizeResponse;
  replayed: boolean;
  idempotencyKey: string;
}

@Injectable({
  providedIn: 'root',
})
export class TransactionsApiService {
  private readonly api = inject(ApiClient);

  listTransactions(params?: QueryParams): Observable<PageResponseTransactionSummary> {
    return this.api.get<PageResponseTransactionSummary>('transactions', params);
  }

  getTransaction(id: string): Observable<TransactionDetail> {
    return this.api.get<TransactionDetail>(`transactions/${id}`);
  }

  exportCsv(params?: QueryParams): Observable<Blob> {
    return this.api.downloadBlob('transactions/export.csv', params);
  }

  openDispute(req: OpenDisputeRequest): Observable<DisputeDetail> {
    return this.api.post<DisputeDetail>('disputes', req);
  }

  authorize(req: AuthorizeRequest, idempotencyKey: string): Observable<AuthorizeResult> {
    return this.api
      .postWithResponse<AuthorizeResponse>('authorize', req, undefined, {
        'Idempotency-Key': idempotencyKey,
      })
      .pipe(
        map((res) => ({
          body: res.body || {},
          replayed: res.headers.get('Idempotent-Replayed') === 'true',
          idempotencyKey,
        })),
      );
  }
}
