import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';

export type QueryParams =
  | HttpParams
  | Record<string, string | number | boolean | readonly (string | number | boolean)[] | undefined>;

@Injectable({
  providedIn: 'root',
})
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly config = inject(APP_CONFIG);

  private buildUrl(path: string): string {
    if (path.startsWith('http://') || path.startsWith('https://')) {
      return path;
    }
    const cleanPath = path.startsWith('/') ? path : `/${path}`;
    return `${this.config.apiBase}${cleanPath}`;
  }

  private toHttpParams(params?: QueryParams): HttpParams {
    if (!params) {
      return new HttpParams();
    }
    if (params instanceof HttpParams) {
      return params;
    }
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        if (Array.isArray(value)) {
          for (const item of value) {
            httpParams = httpParams.append(key, String(item));
          }
        } else {
          httpParams = httpParams.set(key, String(value));
        }
      }
    }
    return httpParams;
  }

  get<T>(path: string, params?: QueryParams, headers?: Record<string, string>): Observable<T> {
    const httpHeaders = headers ? new HttpHeaders(headers) : undefined;
    return this.http.get<T>(this.buildUrl(path), {
      params: this.toHttpParams(params),
      headers: httpHeaders,
    });
  }

  post<T>(
    path: string,
    body: unknown = {},
    params?: QueryParams,
    headers?: Record<string, string>,
  ): Observable<T> {
    const httpHeaders = headers ? new HttpHeaders(headers) : undefined;
    return this.http.post<T>(this.buildUrl(path), body, {
      params: this.toHttpParams(params),
      headers: httpHeaders,
    });
  }

  postWithResponse<T>(
    path: string,
    body: unknown = {},
    params?: QueryParams,
    headers?: Record<string, string>,
  ): Observable<HttpResponse<T>> {
    const httpHeaders = headers ? new HttpHeaders(headers) : undefined;
    return this.http.post<T>(this.buildUrl(path), body, {
      params: this.toHttpParams(params),
      headers: httpHeaders,
      observe: 'response',
    });
  }

  put<T>(
    path: string,
    body: unknown = {},
    params?: QueryParams,
    headers?: Record<string, string>,
  ): Observable<T> {
    const httpHeaders = headers ? new HttpHeaders(headers) : undefined;
    return this.http.put<T>(this.buildUrl(path), body, {
      params: this.toHttpParams(params),
      headers: httpHeaders,
    });
  }

  patch<T>(
    path: string,
    body: unknown = {},
    params?: QueryParams,
    headers?: Record<string, string>,
  ): Observable<T> {
    const httpHeaders = headers ? new HttpHeaders(headers) : undefined;
    return this.http.patch<T>(this.buildUrl(path), body, {
      params: this.toHttpParams(params),
      headers: httpHeaders,
    });
  }

  delete<T>(path: string, params?: QueryParams, headers?: Record<string, string>): Observable<T> {
    const httpHeaders = headers ? new HttpHeaders(headers) : undefined;
    return this.http.delete<T>(this.buildUrl(path), {
      params: this.toHttpParams(params),
      headers: httpHeaders,
    });
  }

  downloadBlob(
    path: string,
    params?: QueryParams,
    headers?: Record<string, string>,
  ): Observable<Blob> {
    const httpHeaders = headers ? new HttpHeaders(headers) : undefined;
    return this.http.get(this.buildUrl(path), {
      params: this.toHttpParams(params),
      headers: httpHeaders,
      responseType: 'blob',
    });
  }
}
