import { Routes } from '@angular/router';

const title = (page: string) => `${page} · STV Bero`;

/** Attendance statistics under `/statistik` (STA), lazy loaded. */
export const STATISTICS_ROUTES: Routes = [
  {
    path: '',
    title: title('Statistik'),
    loadComponent: () => import('./team-statistics').then((m) => m.TeamStatisticsPage),
  },
  {
    path: ':personId',
    title: title('Statistik'),
    loadComponent: () => import('./person-statistics').then((m) => m.PersonStatisticsPage),
  },
];
