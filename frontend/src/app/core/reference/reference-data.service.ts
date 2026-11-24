import { inject, Injectable } from '@angular/core';
import { map, Observable, shareReplay } from 'rxjs';
import { ApiClient } from '../api/api-client';

interface RawMccItem {
  code?: string;
  label?: string;
}

interface RawResponseCodeItem {
  code?: string;
  message?: string;
}

interface RawDeclineReasonItem {
  reason?: string;
  responseCode?: string;
  message?: string;
}

interface RawCurrencyItem {
  code?: string;
  minorUnits?: number;
}

interface RawReferenceDataResponse {
  responseCodes?: RawResponseCodeItem[];
  declineReasons?: RawDeclineReasonItem[];
  mccs?: RawMccItem[];
  currencies?: (RawCurrencyItem | string)[];
  disputeReasons?: string[];
}

export interface ReferenceDataResponse {
  responseCodes: Record<string, string>;
  declineReasons: Record<string, string>;
  mccs: Record<string, string>;
  currencies: string[];
  disputeReasons: string[];
}

@Injectable({
  providedIn: 'root',
})
export class ReferenceDataService {
  private readonly api = inject(ApiClient);
  private referenceData$: Observable<ReferenceDataResponse> | null = null;

  getReferenceData(): Observable<ReferenceDataResponse> {
    if (!this.referenceData$) {
      this.referenceData$ = this.api
        .get<RawReferenceDataResponse>('meta/reference-data')
        .pipe(
          map((data: RawReferenceDataResponse) => {
            const mccs: Record<string, string> = {};
            if (Array.isArray(data.mccs)) {
              for (const item of data.mccs) {
                if (item.code) {
                  mccs[item.code] = item.label || item.code;
                }
              }
            }

            const responseCodes: Record<string, string> = {};
            if (Array.isArray(data.responseCodes)) {
              for (const item of data.responseCodes) {
                if (item.code) {
                  responseCodes[item.code] = item.message || item.code;
                }
              }
            }

            const declineReasons: Record<string, string> = {};
            if (Array.isArray(data.declineReasons)) {
              for (const item of data.declineReasons) {
                if (item.reason) {
                  declineReasons[item.reason] = item.message || item.reason;
                }
              }
            }

            let currencies: string[] = ['NPR', 'USD'];
            if (Array.isArray(data.currencies)) {
              currencies = data.currencies.map((c) =>
                typeof c === 'string' ? c : c.code || 'NPR',
              );
            }

            const disputeReasons = Array.isArray(data.disputeReasons) ? data.disputeReasons : [];

            return {
              responseCodes,
              declineReasons,
              mccs,
              currencies,
              disputeReasons,
            };
          }),
          shareReplay(1),
        );
    }
    return this.referenceData$;
  }
}
