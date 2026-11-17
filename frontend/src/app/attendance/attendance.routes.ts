import { Routes } from '@angular/router';

const title = (page: string) => `${page} · STV Bero`;

/** Routes of the tile «Anwesenheit» under `/termine`, lazy loaded. */
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
    path: ':eventId',
    title: title('Termin'),
    loadComponent: () => import('./event').then((m) => m.EventPage),
  },
  {
    path: ':eventId/aufgebot',
    title: title('Aufgebot'),
    loadComponent: () => import('./call-up').then((m) => m.CallUpPage),
  },
  {
    path: ':eventId/praesenz',
    title: title('Präsenz erfassen'),
    loadComponent: () => import('./attendance-check').then((m) => m.AttendanceCheckPage),
  },
  {
    path: ':eventId/bearbeiten',
    title: title('Termin bearbeiten'),
    loadComponent: () => import('./event-form').then((m) => m.EventForm),
  },
];

/** Series under `/serien` (TER-05, TER-07). */
export const SERIES_ROUTES: Routes = [
  {
    path: ':seriesId',
    title: title('Serie'),
    loadComponent: () => import('./series').then((m) => m.SeriesPage),
  },
  {
    path: ':seriesId/bearbeiten',
    title: title('Serie bearbeiten'),
    loadComponent: () => import('./event-form').then((m) => m.EventForm),
  },
];
