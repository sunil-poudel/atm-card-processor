import { InjectionToken } from '@angular/core';

export interface AppConfig {
  apiBase: string;
  appTitle: string;
  businessZone: string;
}

export const DEFAULT_APP_CONFIG: AppConfig = {
  apiBase: '/api/v1',
  appTitle: 'ATM Card Processor',
  businessZone: 'Asia/Kathmandu',
};

export const APP_CONFIG = new InjectionToken<AppConfig>('APP_CONFIG', {
  providedIn: 'root',
  factory: () => DEFAULT_APP_CONFIG,
});
