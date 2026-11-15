import { Routes } from '@angular/router';

/** Routen der Kachel «Anwesenheit», lazy geladen. */
export const ANWESENHEIT_ROUTES: Routes = [
  {
    path: '',
    title: 'Termine · STV Bero',
    loadComponent: () => import('./termine').then((m) => m.Termine),
  },
];
