import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AnmeldungService } from './anmeldung.service';

/** Läuft die Sitzung während der Nutzung ab (401), zur Anmeldung und danach zurück. */
export const sitzungInterceptor: HttpInterceptorFn = (anfrage, weiter) => {
  const router = inject(Router);
  const anmeldung = inject(AnmeldungService);
  return weiter(anfrage).pipe(
    catchError((fehler: unknown) => {
      const istIch = anfrage.url.endsWith('/api/ich');
      if (
        fehler instanceof HttpErrorResponse &&
        fehler.status === 401 &&
        !istIch &&
        anmeldung.ich()
      ) {
        anmeldung.ich.set(null);
        void router.navigate([anmeldung.anmeldeRoute()], { queryParams: { ziel: router.url } });
      }
      return throwError(() => fehler);
    }),
  );
};
