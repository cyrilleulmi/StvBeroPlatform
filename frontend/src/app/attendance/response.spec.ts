import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import type { EventResponse, ResponseInput, ResponseStatus } from '../api/types';
import { RESPONSE_ICON, ResponseService, responseKey } from './response';

const STATUSES: ResponseStatus[] = ['ATTENDING', 'DECLINED', 'MAYBE', 'PENDING'];

describe('response', () => {
  it('writes the status in lowercase for data attributes and test IDs', () => {
    expect(STATUSES.map(responseKey)).toEqual(['attending', 'declined', 'maybe', 'pending']);
  });

  it('has a distinct icon per status, so colour is not the only carrier (O-16)', () => {
    const icons = STATUSES.map((s) => RESPONSE_ICON[s]);
    expect(icons.every((i) => i.length > 0)).toBe(true);
    expect(new Set(icons).size).toBe(STATUSES.length);
  });

  describe('ResponseService', () => {
    let http: HttpTestingController;
    let service: ResponseService;
    const input = { status: 'ATTENDING' } as ResponseInput;

    beforeEach(() => {
      TestBed.configureTestingModule({
        providers: [provideHttpClient(), provideHttpClientTesting()],
      });
      http = TestBed.inject(HttpTestingController);
      service = TestBed.inject(ResponseService);
    });
    afterEach(() => http.verify());

    it('sets the own response on /responses/me', async () => {
      const result = service.set('e1', input);
      const req = http.expectOne('/api/events/e1/responses/me');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(input);
      req.flush({ status: 'ATTENDING' } as EventResponse);
      expect(await result).toEqual({ status: 'ATTENDING' });
    });

    it('sets the response of another person on her ID (coach)', async () => {
      const result = service.set('e1', input, 'p7');
      http.expectOne('/api/events/e1/responses/p7').flush({});
      await result;
    });

    it('rejects with the HTTP error', async () => {
      const result = service.set('e1', input);
      http
        .expectOne('/api/events/e1/responses/me')
        .flush({ detail: 'x' }, { status: 409, statusText: 'Conflict' });
      await expect(result).rejects.toMatchObject({ status: 409 });
    });
  });
});
