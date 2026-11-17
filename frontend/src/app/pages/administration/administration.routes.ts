import { Routes } from '@angular/router';

const title = (page: string) => `${page} · STV Bero`;

/** Verwaltung (Admin), lazy geladen. Routen nach docs/oberflaeche/navigation.md. */
export const ADMINISTRATION_ROUTES: Routes = [
  {
    path: '',
    title: title('Verwaltung'),
    loadComponent: () => import('./administration').then((m) => m.Administration),
  },
  {
    path: 'personen',
    title: title('Personen'),
    loadComponent: () => import('./people').then((m) => m.People),
  },
  {
    path: 'personen/neu',
    title: title('Person erfassen'),
    loadComponent: () => import('./person-form').then((m) => m.PersonForm),
  },
  {
    path: 'personen/:personId',
    title: title('Person bearbeiten'),
    loadComponent: () => import('./person-form').then((m) => m.PersonForm),
  },
  {
    path: 'teams',
    title: title('Teams verwalten'),
    loadComponent: () => import('./teams-admin').then((m) => m.TeamsAdmin),
  },
  {
    path: 'teams/:teamId',
    title: title('Team verwalten'),
    loadComponent: () => import('./team-admin').then((m) => m.TeamAdmin),
  },
  {
    path: 'saisons',
    title: title('Saisons'),
    loadComponent: () => import('./seasons').then((m) => m.Seasons),
  },
];
