import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AnmeldungService, KeineAktuelleSaison, sicheresZiel } from './auth.service';

/**
 * `/keine-saison` nur zeigen, wenn der Hinweis stimmt (SAI-03, navigation.md: Zugriff «angemeldet»).
 * Fragt immer frisch beim Backend nach:
 * - niemand angemeldet: zur Anmeldung, das Ziel bleibt als `?ziel=` erhalten
 * - Person geladen (es gibt eine aktuelle Saison): direkt weiter zum Ziel
 * - 409 (keine aktuelle Saison): Hinweis anzeigen
 */
export const keineSaisonGuard: CanActivateFn = async (route) => {
  const anmeldung = inject(AnmeldungService);
  const router = inject(Router);
  const ziel: string | undefined = route.queryParamMap.get('ziel') ?? undefined;
  try {
    const ich = await anmeldung.laden();
    if (ich) {
      return router.parseUrl(sicheresZiel(ziel));
    }
  } catch (fehler) {
    if (fehler instanceof KeineAktuelleSaison) {
      return true;
    }
    throw fehler;
  }
  const zielParameter = ziel ? { ziel: sicheresZiel(ziel) } : {};
  return router.createUrlTree([anmeldung.anmeldeRoute()], { queryParams: zielParameter });
};
