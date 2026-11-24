import { Routes } from '@angular/router';

export const USER_ROUTES: Routes = [
  {
    path: '',
    title: 'Staff Users - ATM Card Processor',
    loadComponent: () => import('./users-list.component').then((m) => m.UsersListComponent),
  },
];
