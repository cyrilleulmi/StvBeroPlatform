import { Routes } from '@angular/router';
import { umgebung } from '../umgebung/umgebung';
import { anmeldeGuard } from './kern/anmelde.guard';
import { keineSaisonGuard } from './kern/keine-saison.guard';
import { Shell } from './shell/shell';

const titel = (seite: string) => `${seite} · STV Bero`;

/** Routen nach docs/oberflaeche/navigation.md. */
export const routes: Routes = [
  {
    path: 'anmelden',
    title: titel('Anmelden'),
    loadComponent: () => import('./seiten/anmelden/anmelden').then((m) => m.Anmelden),
  },
  {
    path: 'keine-saison',
    title: titel('Keine aktuelle Saison'),
    canActivate: [keineSaisonGuard],
    loadComponent: () => import('./seiten/keine-saison/keine-saison').then((m) => m.KeineSaison),
  },
  ...(umgebung.testmodus
    ? [
        {
          path: 'dev-login',
          title: titel('Testmodus'),
          loadComponent: () => import('./seiten/dev-login/dev-login').then((m) => m.DevLogin),
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
        loadComponent: () => import('./seiten/start/start').then((m) => m.Start),
      },
      {
        path: 'termine',
        loadChildren: () =>
          import('./anwesenheit/anwesenheit.routes').then((m) => m.ANWESENHEIT_ROUTES),
      },
      {
        path: 'teams',
        title: titel('Teams'),
        loadComponent: () => import('./seiten/teams/teams').then((m) => m.Teams),
      },
      {
        path: 'teams/:teamId',
        title: titel('Team'),
        loadComponent: () => import('./seiten/teams/team').then((m) => m.Team),
      },
      {
        path: 'profil',
        title: titel('Profil'),
        loadComponent: () => import('./seiten/profil/profil').then((m) => m.Profil),
      },
      {
        path: 'verwaltung',
        loadChildren: () =>
          import('./seiten/verwaltung/verwaltung.routes').then((m) => m.VERWALTUNG_ROUTES),
      },
      {
        path: '**',
        title: titel('Nicht gefunden'),
        loadComponent: () =>
          import('./seiten/nicht-gefunden/nicht-gefunden').then((m) => m.NichtGefunden),
      },
    ],
  },
];
