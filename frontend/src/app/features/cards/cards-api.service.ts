import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient, QueryParams } from '../../core/api/api-client';
import { components } from '../../core/api/schema';

export type CardResponse = components['schemas']['CardResponse'];
export type IssueCardRequest = components['schemas']['IssueCardRequest'];
export type LedgerEntryResponse = components['schemas']['LedgerEntryResponse'];
export type TransactionSummary = components['schemas']['TransactionSummary'];
export type PageResponseCardResponse = components['schemas']['PageResponseCardResponse'];
export type PageResponseLedgerEntryResponse =
  components['schemas']['PageResponseLedgerEntryResponse'];
export type PageResponseTransactionSummary =
  components['schemas']['PageResponseTransactionSummary'];

@Injectable({
  providedIn: 'root',
})
export class CardsApiService {
  private readonly api = inject(ApiClient);

  listCards(params?: QueryParams): Observable<PageResponseCardResponse> {
    return this.api.get<PageResponseCardResponse>('cards', params);
  }

  getCard(id: string): Observable<CardResponse> {
    return this.api.get<CardResponse>(`cards/${id}`);
  }

  issueCard(req: IssueCardRequest): Observable<CardResponse> {
    return this.api.post<CardResponse>('cards', req);
  }

  activateCard(id: string): Observable<CardResponse> {
    return this.api.post<CardResponse>(`cards/${id}/activate`);
  }

  blockCard(id: string, reason: string): Observable<CardResponse> {
    return this.api.post<CardResponse>(`cards/${id}/block`, { reason });
  }

  unblockCard(id: string): Observable<CardResponse> {
    return this.api.post<CardResponse>(`cards/${id}/unblock`);
  }

  closeCard(id: string): Observable<CardResponse> {
    return this.api.post<CardResponse>(`cards/${id}/close`);
  }

  topUpCard(
    id: string,
    amountMinor: number,
    description?: string,
  ): Observable<LedgerEntryResponse> {
    return this.api.post<LedgerEntryResponse>(`cards/${id}/topups`, {
      amountMinor,
      description,
    });
  }

  updateLimits(
    id: string,
    perTransactionLimitMinor: number,
    dailyLimitMinor: number,
    version?: number,
  ): Observable<CardResponse> {
    return this.api.put<CardResponse>(`cards/${id}/limits`, {
      perTransactionLimitMinor,
      dailyLimitMinor,
      version,
    });
  }

  updateBlockedMccs(id: string, blockedMccs: string[], version?: number): Observable<CardResponse> {
    return this.api.put<CardResponse>(`cards/${id}/blocked-mccs`, {
      blockedMccs,
      version,
    });
  }

  getCardLedger(id: string, params?: QueryParams): Observable<PageResponseLedgerEntryResponse> {
    return this.api.get<PageResponseLedgerEntryResponse>(`cards/${id}/ledger`, params);
  }

  getCardTransactions(
    id: string,
    params?: QueryParams,
  ): Observable<PageResponseTransactionSummary> {
    return this.api.get<PageResponseTransactionSummary>(`cards/${id}/transactions`, params);
  }
}
