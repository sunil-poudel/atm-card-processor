import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiClient } from '../../core/api/api-client';
import { components } from '../../core/api/schema';

export type DailyReportResponse = components['schemas']['DailyReportResponse'];
export type SummaryReportResponse = components['schemas']['SummaryReportResponse'];
export type VolumeByCurrency = components['schemas']['VolumeByCurrency'];
export type DeclinesByCode = components['schemas']['DeclinesByCode'];
export type TopMcc = components['schemas']['TopMcc'];
export type HourlyBucket = components['schemas']['HourlyBucket'];
export type DailySeriesEntry = components['schemas']['DailySeriesEntry'];

@Injectable({
  providedIn: 'root',
})
export class ReportsApiService {
  private readonly api = inject(ApiClient);

  getDailyReport(date?: string): Observable<DailyReportResponse> {
    const params = date ? { date } : undefined;
    return this.api.get<DailyReportResponse>('reports/daily', params);
  }

  getSummaryReport(from?: string, to?: string): Observable<SummaryReportResponse> {
    const params: Record<string, string> = {};
    if (from) params['from'] = from;
    if (to) params['to'] = to;
    return this.api.get<SummaryReportResponse>(
      'reports/summary',
      Object.keys(params).length ? params : undefined,
    );
  }
}
