import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AnmeldungService, KeineAktuelleSaison } from './auth.service';

/**
 * Ohne Anmeldung auf die Anmeldung umleiten, mit dem Ziel als `?ziel=` (danach zurück).
 * Ohne aktuelle Saison (409 von `GET /api/me`, SAI-03) auf den Hinweis `/keine-saison`.
 */
export const anmeldeGuard: CanActivateFn = async (_route, state) => {
  const anmeldung = inject(AnmeldungService);
  const router = inject(Router);
  const zielParameter = state.url === '/' ? {} : { ziel: state.url };
  try {
    const ich = anmeldung.ich() ?? (await anmeldung.laden());
    if (ich) {
      return true;
    }
  } catch (fehler) {
    if (fehler instanceof KeineAktuelleSaison) {
      return router.createUrlTree(['/keine-saison'], { queryParams: zielParameter });
    }
    throw fehler;
  }
  return router.createUrlTree([anmeldung.anmeldeRoute()], { queryParams: zielParameter });
};
