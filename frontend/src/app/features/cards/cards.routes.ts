import { Routes } from '@angular/router';

export const CARD_ROUTES: Routes = [
  {
    path: '',
    title: 'Cards - ATM Card Processor',
    loadComponent: () => import('./cards-list.component').then((m) => m.CardsListComponent),
  },
  {
    path: ':id',
    title: 'Card Details - ATM Card Processor',
    loadComponent: () => import('./card-detail.component').then((m) => m.CardDetailComponent),
  },
];
