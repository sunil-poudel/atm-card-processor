import { Routes } from '@angular/router';

export const API_CLIENT_ROUTES: Routes = [
  {
    path: '',
    title: 'API Clients - ATM Card Processor',
    loadComponent: () =>
      import('./api-clients-list.component').then((m) => m.ApiClientsListComponent),
  },
];
