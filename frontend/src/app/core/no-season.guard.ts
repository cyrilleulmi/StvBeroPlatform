import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, NoCurrentSeasonError, safeTarget } from './auth.service';

/**
 * Only show `/keine-saison` when the notice is true (SAI-03, navigation.md: access «logged in»).
 * Always asks the backend afresh:
 * - nobody logged in: to the login, the target is kept as `?ziel=`
 * - person loaded (there is a current season): straight on to the target
 * - 409 (no current season): show the notice
 */
export const noSeasonGuard: CanActivateFn = async (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const target: string | undefined = route.queryParamMap.get('ziel') ?? undefined;
  try {
    const me = await auth.load();
    if (me) {
      return router.parseUrl(safeTarget(target));
    }
  } catch (error) {
    if (error instanceof NoCurrentSeasonError) {
      return true;
    }
    throw error;
  }
  const targetParams = target ? { ziel: safeTarget(target) } : {};
  return router.createUrlTree([auth.loginRoute()], { queryParams: targetParams });
};
