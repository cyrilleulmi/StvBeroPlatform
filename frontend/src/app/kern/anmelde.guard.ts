import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AnmeldungService } from './anmeldung.service';

/** Ohne Anmeldung auf die Anmeldung umleiten, mit dem Ziel als `?ziel=` (danach zurück). */
export const anmeldeGuard: CanActivateFn = async (_route, state) => {
  const anmeldung = inject(AnmeldungService);
  const router = inject(Router);
  const ich = anmeldung.ich() ?? (await anmeldung.laden());
  if (ich) {
    return true;
  }
  return router.createUrlTree([anmeldung.anmeldeRoute()], {
    queryParams: state.url === '/' ? {} : { ziel: state.url },
  });
};
