import type { components } from './schema';

/** Typen aus docs/api/openapi.yaml (erzeugt mit `npm run api-typen`). */
export type Ich = components['schemas']['Ich'];
export type Sitzung = components['schemas']['Sitzung'];
export type Kachel = components['schemas']['Kachel'];
export type TestPerson = components['schemas']['TestPerson'];
export type Fehler = components['schemas']['Fehler'];
