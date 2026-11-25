import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { sessionInterceptor } from './session.interceptor';

function setup(signedIn: boolean) {
  const me = signal(signedIn ? { id: 'p1' } : null);
  const navigate = vi.fn().mockResolvedValue(true);
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([sessionInterceptor])),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { me, loginRoute: () => '/anmelden' } },
      { provide: Router, useValue: { navigate, url: '/termine/e1' } },
    ],
  });
  return {
    me,
    navigate,
    http: TestBed.inject(HttpClient),
    ctrl: TestBed.inject(HttpTestingController),
  };
}

describe('sessionInterceptor', () => {
  it('goes to the login and remembers the page when the session expires (401)', async () => {
    const { me, navigate, http, ctrl } = setup(true);
    const result = http.get('/api/events').toPromise();
    ctrl.expectOne('/api/events').flush({}, { status: 401, statusText: 'Unauthorized' });
    await expect(result).rejects.toMatchObject({ status: 401 });
    expect(me()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/anmelden'], { queryParams: { ziel: '/termine/e1' } });
  });

  it('does not redirect for the 401 of /api/me (that is the normal «not signed in»)', async () => {
    const { navigate, http, ctrl } = setup(true);
    const result = http.get('/api/me').toPromise();
    ctrl.expectOne('/api/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    await expect(result).rejects.toMatchObject({ status: 401 });
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not redirect if nobody was signed in', async () => {
    const { navigate, http, ctrl } = setup(false);
    const result = http.get('/api/events').toPromise();
    ctrl.expectOne('/api/events').flush({}, { status: 401, statusText: 'x' });
    await expect(result).rejects.toBeDefined();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('passes other errors through without redirecting', async () => {
    const { me, navigate, http, ctrl } = setup(true);
    const result = http.get('/api/events').toPromise();
    ctrl.expectOne('/api/events').flush({}, { status: 500, statusText: 'x' });
    await expect(result).rejects.toMatchObject({ status: 500 });
    expect(me()).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });
});
