import { Routes } from '@angular/router';

export const AUDIT_ROUTES: Routes = [
  {
    path: '',
    title: 'Audit Log - ATM Card Processor',
    loadComponent: () => import('./audit-log.component').then((m) => m.AuditLogComponent),
  },
];
