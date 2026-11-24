import { HttpErrorResponse } from '@angular/common/http';
import { errorRule, errorStatus, errorText, fieldErrorsOf } from './error-text';
import { TEXTS } from './texts';

const http = (status: number, body: unknown = null) =>
  new HttpErrorResponse({ status, error: body });

describe('error-text', () => {
  describe('errorText', () => {
    it('says «Keine Berechtigung» for 403, even if there is a detail', () => {
      expect(errorText(http(403, { detail: 'Something internal' }))).toBe(
        TEXTS.common.noPermission,
      );
    });

    it('uses the detail of the problem details', () => {
      expect(errorText(http(409, { detail: 'Der Termin ist schon vorbei.' }))).toBe(
        'Der Termin ist schon vorbei.',
      );
    });

    it('falls back to the generic text without a usable detail', () => {
      expect(errorText(http(500))).toBe(TEXTS.common.loadError);
      expect(errorText(http(500, { detail: '' }))).toBe(TEXTS.common.loadError);
      expect(errorText(http(500, { detail: 42 }))).toBe(TEXTS.common.loadError);
      expect(errorText(http(500, 'plain text body'))).toBe(TEXTS.common.loadError);
    });

    it('falls back to the generic text for errors that are not HTTP errors', () => {
      expect(errorText(new Error('boom'))).toBe(TEXTS.common.loadError);
      expect(errorText(undefined)).toBe(TEXTS.common.loadError);
      expect(errorText(null)).toBe(TEXTS.common.loadError);
    });
  });

  describe('errorStatus', () => {
    it('returns the HTTP status, otherwise 0', () => {
      expect(errorStatus(http(404))).toBe(404);
      expect(errorStatus(new Error('x'))).toBe(0);
      expect(errorStatus(null)).toBe(0);
    });
  });

  describe('errorRule', () => {
    it('returns the rule ID of the problem details', () => {
      expect(errorRule(http(409, { rule: 'TER-12' }))).toBe('TER-12');
    });

    it('is null without a string rule or for other errors', () => {
      expect(errorRule(http(409, {}))).toBeNull();
      expect(errorRule(http(409, { rule: 12 }))).toBeNull();
      expect(errorRule(http(409))).toBeNull();
      expect(errorRule(new Error('x'))).toBeNull();
    });
  });

  describe('fieldErrorsOf', () => {
    const body = {
      fields: [
        { field: 'firstName', message: 'Pflichtfeld' },
        { field: 'start', message: 'Zu früh' },
      ],
    };

    it('maps fields to messages', () => {
      expect(fieldErrorsOf(http(422, body))).toEqual({
        firstName: 'Pflichtfeld',
        start: 'Zu früh',
      });
    });

    it('translates API field names with the field map and keeps the others', () => {
      expect(fieldErrorsOf(http(422, body), { start: 'startTime' })).toEqual({
        firstName: 'Pflichtfeld',
        startTime: 'Zu früh',
      });
    });

    it('is empty without fields or for other errors', () => {
      expect(fieldErrorsOf(http(422))).toEqual({});
      expect(fieldErrorsOf(http(422, { fields: null }))).toEqual({});
      expect(fieldErrorsOf(new Error('x'))).toEqual({});
    });

    it('keeps the last message if a field occurs twice', () => {
      const twice = {
        fields: [
          { field: 'a', message: 'one' },
          { field: 'a', message: 'two' },
        ],
      };
      expect(fieldErrorsOf(http(422, twice))).toEqual({ a: 'two' });
    });
  });
});
