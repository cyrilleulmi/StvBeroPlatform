import { Routes } from '@angular/router';
import { umgebung } from '../environments/environment';
import { anmeldeGuard } from './core/auth.guard';
import { keineSaisonGuard } from './core/no-season.guard';
import { Shell } from './shell/shell';

const titel = (seite: string) => `${seite} · STV Bero`;

/** Routen nach docs/oberflaeche/navigation.md. */
export const routes: Routes = [
  {
    path: 'anmelden',
    title: titel('Anmelden'),
    loadComponent: () => import('./pages/login/login').then((m) => m.Anmelden),
  },
  {
    path: 'keine-saison',
    title: titel('Keine aktuelle Saison'),
    canActivate: [keineSaisonGuard],
    loadComponent: () => import('./pages/no-season/no-season').then((m) => m.KeineSaison),
  },
  ...(umgebung.testmodus
    ? [
        {
          path: 'dev-login',
          title: titel('Testmodus'),
          loadComponent: () => import('./pages/dev-login/dev-login').then((m) => m.DevLogin),
        },
      ]
    : []),
  {
    path: '',
    component: Shell,
    canActivate: [anmeldeGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: titel('Start'),
        loadComponent: () => import('./pages/start/start').then((m) => m.Start),
      },
      {
        path: 'termine',
        loadChildren: () =>
          import('./attendance/attendance.routes').then((m) => m.ANWESENHEIT_ROUTES),
      },
      {
        path: 'serien',
        loadChildren: () => import('./attendance/attendance.routes').then((m) => m.SERIEN_ROUTES),
      },
      {
        path: 'teams',
        title: titel('Teams'),
        loadComponent: () => import('./pages/teams/teams').then((m) => m.Teams),
      },
      {
        path: 'teams/:teamId',
        title: titel('Team'),
        loadComponent: () => import('./pages/teams/team').then((m) => m.Team),
      },
      {
        path: 'teams/:teamId/linien',
        title: titel('Linien'),
        loadComponent: () => import('./pages/teams/lines').then((m) => m.Linien),
      },
      {
        path: 'benachrichtigungen',
        title: titel('Benachrichtigungen'),
        loadComponent: () =>
          import('./pages/notifications/notifications').then((m) => m.Benachrichtigungen),
      },
      {
        path: 'profil',
        title: titel('Profil'),
        loadComponent: () => import('./pages/profile/profile').then((m) => m.Profil),
      },
      {
        path: 'verwaltung',
        loadChildren: () =>
          import('./pages/administration/administration.routes').then((m) => m.VERWALTUNG_ROUTES),
      },
      {
        path: '**',
        title: titel('Nicht gefunden'),
        loadComponent: () =>
          import('./pages/not-found/not-found').then((m) => m.NichtGefunden),
      },
    ],
  },
];
