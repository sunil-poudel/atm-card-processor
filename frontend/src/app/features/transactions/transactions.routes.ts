import { Routes } from '@angular/router';

export const TRANSACTION_ROUTES: Routes = [
  {
    path: '',
    title: 'Transactions - ATM Card Processor',
    loadComponent: () =>
      import('./transactions-list.component').then((m) => m.TransactionsListComponent),
  },
];
