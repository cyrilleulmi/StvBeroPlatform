import { Routes } from '@angular/router';

const titel = (seite: string) => `${seite} · STV Bero`;

/** Verwaltung (Admin), lazy geladen. Routen nach docs/oberflaeche/navigation.md. */
export const VERWALTUNG_ROUTES: Routes = [
  {
    path: '',
    title: titel('Verwaltung'),
    loadComponent: () => import('./verwaltung').then((m) => m.Verwaltung),
  },
  {
    path: 'personen',
    title: titel('Personen'),
    loadComponent: () => import('./personen').then((m) => m.Personen),
  },
  {
    path: 'personen/neu',
    title: titel('Person erfassen'),
    loadComponent: () => import('./person-formular').then((m) => m.PersonFormular),
  },
  {
    path: 'personen/:personId',
    title: titel('Person bearbeiten'),
    loadComponent: () => import('./person-formular').then((m) => m.PersonFormular),
  },
  {
    path: 'teams',
    title: titel('Teams verwalten'),
    loadComponent: () => import('./teams-verwaltung').then((m) => m.TeamsVerwaltung),
  },
  {
    path: 'teams/:teamId',
    title: titel('Team verwalten'),
    loadComponent: () => import('./team-verwaltung').then((m) => m.TeamVerwaltung),
  },
  {
    path: 'saisons',
    title: titel('Saisons'),
    loadComponent: () => import('./saisons').then((m) => m.Saisons),
  },
];
