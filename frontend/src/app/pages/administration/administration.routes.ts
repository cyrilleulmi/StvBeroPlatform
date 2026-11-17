import { Routes } from '@angular/router';

const titel = (seite: string) => `${seite} · STV Bero`;

/** Verwaltung (Admin), lazy geladen. Routen nach docs/oberflaeche/navigation.md. */
export const VERWALTUNG_ROUTES: Routes = [
  {
    path: '',
    title: titel('Verwaltung'),
    loadComponent: () => import('./administration').then((m) => m.Verwaltung),
  },
  {
    path: 'personen',
    title: titel('Personen'),
    loadComponent: () => import('./people').then((m) => m.Personen),
  },
  {
    path: 'personen/neu',
    title: titel('Person erfassen'),
    loadComponent: () => import('./person-form').then((m) => m.PersonFormular),
  },
  {
    path: 'personen/:personId',
    title: titel('Person bearbeiten'),
    loadComponent: () => import('./person-form').then((m) => m.PersonFormular),
  },
  {
    path: 'teams',
    title: titel('Teams verwalten'),
    loadComponent: () => import('./teams-admin').then((m) => m.TeamsVerwaltung),
  },
  {
    path: 'teams/:teamId',
    title: titel('Team verwalten'),
    loadComponent: () => import('./team-admin').then((m) => m.TeamVerwaltung),
  },
  {
    path: 'saisons',
    title: titel('Saisons'),
    loadComponent: () => import('./seasons').then((m) => m.Saisons),
  },
];
