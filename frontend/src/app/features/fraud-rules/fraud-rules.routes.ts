import { Routes } from '@angular/router';

export const FRAUD_RULES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./fraud-rules-list.component').then((m) => m.FraudRulesListComponent),
  },
];
