import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** If the session expires during use (401), go to the login and come back afterwards. */
export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  const router = inject(Router);
  const auth = inject(AuthService);
  return next(request).pipe(
    catchError((error: unknown) => {
      const isMe = request.url.endsWith('/api/me');
      if (error instanceof HttpErrorResponse && error.status === 401 && !isMe && auth.me()) {
        auth.me.set(null);
        void router.navigate([auth.loginRoute()], { queryParams: { ziel: router.url } });
      }
      return throwError(() => error);
    }),
  );
};
