import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign In - ATM Card Processor',
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '403',
    title: 'Access Denied - ATM Card Processor',
    loadComponent: () =>
      import('./features/system/forbidden.component').then((m) => m.ForbiddenComponent),
  },
  {
    path: '',
    loadComponent: () => import('./layout/shell.component').then((m) => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'dashboard',
      },
      {
        path: 'dashboard',
        title: 'Dashboard - ATM Card Processor',
        data: { roles: ['ADMIN', 'ANALYST'], title: 'Dashboard' },
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        path: 'cards',
        data: { roles: ['ADMIN', 'ANALYST', 'DISPUTE_OFFICER'] },
        canActivate: [roleGuard],
        loadChildren: () => import('./features/cards/cards.routes').then((m) => m.CARD_ROUTES),
      },
      {
        path: 'transactions',
        data: { roles: ['ADMIN', 'ANALYST', 'DISPUTE_OFFICER'] },
        canActivate: [roleGuard],
        loadChildren: () =>
          import('./features/transactions/transactions.routes').then((m) => m.TRANSACTION_ROUTES),
      },
      {
        path: 'simulator',
        title: 'Payment Simulator - ATM Card Processor',
        data: { roles: ['ADMIN'], title: 'Payment Simulator' },
        canActivate: [roleGuard],
        loadComponent: () =>
          import('./features/transactions/simulator/payment-simulator.component').then(
            (m) => m.PaymentSimulatorComponent,
          ),
      },
      {
        path: 'fraud-rules',
        title: 'Fraud Rules - ATM Card Processor',
        data: { roles: ['ADMIN', 'ANALYST'], title: 'Fraud Rules' },
        canActivate: [roleGuard],
        loadChildren: () =>
          import('./features/fraud-rules/fraud-rules.routes').then((m) => m.FRAUD_RULES_ROUTES),
      },
      {
        path: 'disputes',
        data: { roles: ['ADMIN', 'ANALYST', 'DISPUTE_OFFICER'] },
        canActivate: [roleGuard],
        loadChildren: () =>
          import('./features/disputes/disputes.routes').then((m) => m.DISPUTE_ROUTES),
      },
      {
        path: 'reports',
        title: 'Reports - ATM Card Processor',
        data: { roles: ['ADMIN', 'ANALYST'], title: 'Reports' },
        canActivate: [roleGuard],
        loadChildren: () =>
          import('./features/reports/reports.routes').then((m) => m.REPORT_ROUTES),
      },
      {
        path: 'admin/users',
        title: 'User Management - ATM Card Processor',
        data: { roles: ['ADMIN'], title: 'User Management' },
        canActivate: [roleGuard],
        loadChildren: () => import('./features/users/users.routes').then((m) => m.USER_ROUTES),
      },
      {
        path: 'admin/api-clients',
        title: 'API Clients - ATM Card Processor',
        data: { roles: ['ADMIN'], title: 'API Clients' },
        canActivate: [roleGuard],
        loadChildren: () =>
          import('./features/api-clients/api-clients.routes').then((m) => m.API_CLIENT_ROUTES),
      },
      {
        path: 'admin/audit',
        title: 'Audit Log - ATM Card Processor',
        data: { roles: ['ADMIN'], title: 'Audit Log' },
        canActivate: [roleGuard],
        loadChildren: () => import('./features/audit/audit.routes').then((m) => m.AUDIT_ROUTES),
      },
    ],
  },
  {
    path: '**',
    title: 'Page Not Found - ATM Card Processor',
    loadComponent: () =>
      import('./features/system/not-found.component').then((m) => m.NotFoundComponent),
  },
];
