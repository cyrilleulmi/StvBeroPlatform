import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, NoCurrentSeasonError } from './auth.service';

/**
 * Without login redirect to the login, with the target as `?ziel=` (back there afterwards).
 * Without a current season (409 from `GET /api/me`, SAI-03) redirect to the notice `/keine-saison`.
 */
export const authGuard: CanActivateFn = async (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const targetParams = state.url === '/' ? {} : { ziel: state.url };
  try {
    const me = auth.me() ?? (await auth.load());
    if (me) {
      return true;
    }
  } catch (error) {
    if (error instanceof NoCurrentSeasonError) {
      return router.createUrlTree(['/keine-saison'], { queryParams: targetParams });
    }
    throw error;
  }
  return router.createUrlTree([auth.loginRoute()], { queryParams: targetParams });
};
