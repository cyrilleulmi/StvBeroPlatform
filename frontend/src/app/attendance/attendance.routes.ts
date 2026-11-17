import { Routes } from '@angular/router';

const title = (page: string) => `${page} · STV Bero`;

/** Routen der Kachel «Anwesenheit» unter `/termine`, lazy geladen. */
export const ATTENDANCE_ROUTES: Routes = [
  {
    path: '',
    title: title('Termine'),
    loadComponent: () => import('./events').then((m) => m.Events),
  },
  {
    path: 'neu',
    title: title('Termin erfassen'),
    loadComponent: () => import('./event-form').then((m) => m.EventForm),
  },
  {
    path: ':terminId',
    title: title('Termin'),
    loadComponent: () => import('./event').then((m) => m.EventPage),
  },
  {
    path: ':terminId/aufgebot',
    title: title('Aufgebot'),
    loadComponent: () => import('./call-up').then((m) => m.CallUpPage),
  },
  {
    path: ':terminId/bearbeiten',
    title: title('Termin bearbeiten'),
    loadComponent: () => import('./event-form').then((m) => m.EventForm),
  },
];

/** Serien unter `/serien` (TER-05, TER-07). */
export const SERIES_ROUTES: Routes = [
  {
    path: ':serieId',
    title: title('Serie'),
    loadComponent: () => import('./series').then((m) => m.SeriesPage),
  },
  {
    path: ':serieId/bearbeiten',
    title: title('Serie bearbeiten'),
    loadComponent: () => import('./event-form').then((m) => m.EventForm),
  },
];
