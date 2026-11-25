import { Routes } from '@angular/router';
import { environment } from '../environments/environment';
import { authGuard } from './core/auth.guard';
import { noSeasonGuard } from './core/no-season.guard';
import { Shell } from './shell/shell';

const title = (page: string) => `${page} · STV Bero`;

/** Routes according to docs/ui/navigation.md. */
export const routes: Routes = [
  {
    path: 'anmelden',
    title: title('Anmelden'),
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'keine-saison',
    title: title('Keine aktuelle Saison'),
    canActivate: [noSeasonGuard],
    loadComponent: () => import('./pages/no-season/no-season').then((m) => m.NoSeason),
  },
  ...(environment.testMode
    ? [
        {
          path: 'dev-login',
          title: title('Testmodus'),
          loadComponent: () => import('./pages/dev-login/dev-login').then((m) => m.DevLogin),
        },
      ]
    : []),
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        title: title('Start'),
        loadComponent: () => import('./pages/start/start').then((m) => m.Start),
      },
      {
        path: 'termine/ueberarbeiten',
        title: title('Termine überarbeiten'),
        loadComponent: () =>
          import('./pages/administration/events-rework').then((m) => m.EventsRework),
      },
      {
        path: 'termine',
        loadChildren: () =>
          import('./attendance/attendance.routes').then((m) => m.ATTENDANCE_ROUTES),
      },
      {
        path: 'serien',
        loadChildren: () => import('./attendance/attendance.routes').then((m) => m.SERIES_ROUTES),
      },
      {
        path: 'statistik',
        loadChildren: () =>
          import('./statistics/statistics.routes').then((m) => m.STATISTICS_ROUTES),
      },
      {
        path: 'fahrten',
        title: title('Fahrten'),
        loadComponent: () => import('./drivers/trips').then((m) => m.TripsPage),
      },
      {
        path: 'fahrerliste',
        title: title('Fahrerliste'),
        loadComponent: () => import('./drivers/driver-plan-entry').then((m) => m.DriverPlanEntry),
      },
      {
        path: 'swissunihockey',
        title: title('swiss unihockey'),
        loadComponent: () =>
          import('./swissunihockey/swiss-unihockey').then((m) => m.SwissUnihockeyPage),
      },
      {
        path: 'teams',
        title: title('Teams'),
        loadComponent: () => import('./pages/teams/teams').then((m) => m.Teams),
      },
      {
        path: 'teams/:teamId',
        title: title('Team'),
        loadComponent: () => import('./pages/teams/team').then((m) => m.Team),
      },
      {
        path: 'teams/:teamId/linien',
        title: title('Linien'),
        loadComponent: () => import('./pages/teams/lines').then((m) => m.Lines),
      },
      {
        path: 'teams/:teamId/fahrerliste',
        title: title('Fahrerliste'),
        loadComponent: () => import('./drivers/driver-plan').then((m) => m.DriverPlanPage),
      },
      {
        path: 'teams/:teamId/liga',
        title: title('Liga'),
        loadComponent: () => import('./swissunihockey/league').then((m) => m.League),
      },
      {
        path: 'benachrichtigungen',
        title: title('Benachrichtigungen'),
        loadComponent: () => import('./notifications/notifications').then((m) => m.Notifications),
      },
      {
        path: 'profil',
        title: title('Profil'),
        loadComponent: () => import('./pages/profile/profile').then((m) => m.Profile),
      },
      {
        path: 'profil/abwesenheiten',
        title: title('Abwesenheiten'),
        loadComponent: () => import('./attendance/absences').then((m) => m.AbsencesPage),
      },
      {
        path: 'profil/benachrichtigungen',
        title: title('Benachrichtigungen'),
        loadComponent: () =>
          import('./pages/profile/profile-notifications').then((m) => m.ProfileNotificationsPage),
      },
      {
        path: 'profil/kalender',
        title: title('Kalender abonnieren'),
        loadComponent: () =>
          import('./pages/profile/profile-calendar').then((m) => m.ProfileCalendarPage),
      },
      {
        path: 'profil/fahren',
        title: title('Fahren'),
        loadComponent: () =>
          import('./pages/profile/profile-driving').then((m) => m.ProfileDrivingPage),
      },
      {
        path: 'profil/statistik',
        title: title('Meine Statistik'),
        loadComponent: () =>
          import('./statistics/person-statistics').then((m) => m.PersonStatisticsPage),
      },
      {
        path: 'verwaltung',
        loadChildren: () =>
          import('./pages/administration/administration.routes').then(
            (m) => m.ADMINISTRATION_ROUTES,
          ),
      },
      {
        path: '**',
        title: title('Nicht gefunden'),
        loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFound),
      },
    ],
  },
];
