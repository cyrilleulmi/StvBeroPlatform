import { Routes } from '@angular/router';

const titel = (seite: string) => `${seite} · STV Bero`;

/** Routen der Kachel «Anwesenheit» unter `/termine`, lazy geladen. */
export const ANWESENHEIT_ROUTES: Routes = [
  {
    path: '',
    title: titel('Termine'),
    loadComponent: () => import('./termine').then((m) => m.Termine),
  },
  {
    path: 'neu',
    title: titel('Termin erfassen'),
    loadComponent: () => import('./termin-formular').then((m) => m.TerminFormular),
  },
  {
    path: ':terminId',
    title: titel('Termin'),
    loadComponent: () => import('./termin').then((m) => m.Termin),
  },
  {
    path: ':terminId/bearbeiten',
    title: titel('Termin bearbeiten'),
    loadComponent: () => import('./termin-formular').then((m) => m.TerminFormular),
  },
];

/** Serien unter `/serien` (TER-05, TER-07). */
export const SERIEN_ROUTES: Routes = [
  {
    path: ':serieId',
    title: titel('Serie'),
    loadComponent: () => import('./serie').then((m) => m.Serie),
  },
  {
    path: ':serieId/bearbeiten',
    title: titel('Serie bearbeiten'),
    loadComponent: () => import('./termin-formular').then((m) => m.TerminFormular),
  },
];
