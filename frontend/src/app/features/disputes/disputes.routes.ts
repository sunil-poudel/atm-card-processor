import { Routes } from '@angular/router';

export const DISPUTE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./disputes-list.component').then((m) => m.DisputesListComponent),
  },
  {
    path: ':id',
    loadComponent: () => import('./dispute-detail.component').then((m) => m.DisputeDetailComponent),
  },
];
