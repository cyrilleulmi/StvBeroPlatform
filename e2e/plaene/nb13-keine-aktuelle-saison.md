# Testplan Nachbesserung #13 · Keine aktuelle Saison (409 von GET /api/ich)

Issue #13 · PR #25 · Anforderung SAI-03 · Szenario S23 (testdaten.md)

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`, dann `DELETE /api/test/aktuelle-saison` (ausser anders angegeben),
  dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }` oder über `/dev-login`.
- Uhr: Standard, die Uhr spielt hier keine Rolle.
- Ansichten: mobil 375×812 (alle Fälle), Desktop 1280×800 (K2.1, K2.3). Hinweis-Screen einmal im Darkmode.
- Nach jedem Fall: Konsolenfehler prüfen. Erlaubt ist nur der Browser-Eintrag «Failed to load resource … 409»
  zu `GET /api/ich`. Keine anderen 4xx/5xx.

## K1 · API-Vertrag (Kriterium 1)

| Fall | Schritte | Erwartung |
|---|---|---|
| K1.1 Vertrag | `docs/api/openapi.yaml`, Pfad `/api/ich` `get` lesen | Antwort `'409'` mit `#/components/responses/Konflikt` |
| K1.2 Lint | `npx @redocly/cli lint docs/api/openapi.yaml` | Keine Fehler, keine Warnungen |
| K1.3 Laufzeit-Vertrag | `GET /q/openapi?format=json`, Pfad `/api/ich` | Statuscodes 200, 401, 409 |
| K1.4 Antwort 409 | Ohne aktuelle Saison als `anna` und als `alex` `GET /api/ich` | 409, `application/problem+json`, `title` «Keine aktuelle Saison», `regel` «SAI-03» |
| K1.5 Ohne Sitzung | Ohne aktuelle Saison, ohne Cookie `GET /api/ich` | 401 (nicht 409) |
| K1.6 Test-Hilfe | `DELETE /api/test/aktuelle-saison`, dann `POST /api/test/reset` | 204, danach ist «2026/27» wieder aktuell, `GET /api/ich` 200 |

## K2 · Hinweis in der Oberfläche (Kriterium 2)

| Fall | Schritte | Erwartung |
|---|---|---|
| K2.1 Deep Link | Als `anna` `/teams` öffnen | Weiter auf `/keine-saison?ziel=%2Fteams`, Titel «Keine aktuelle Saison · STV Bero», Überschrift «Noch keine aktuelle Saison», `keine-saison-hinweis` mit Erklärung, Buttons «Nochmals versuchen» und «Abmelden». Kein leerer Bildschirm, keine Navigation |
| K2.2 Startseite | Als `anna` `/` öffnen | `/keine-saison` (ohne `ziel`) |
| K2.3 Admin | Als `alex` `/verwaltung/saisons` öffnen | Ebenfalls `/keine-saison?ziel=%2Fverwaltung%2Fsaisons` (Annahme O-36) |
| K2.4 Coach über Testmodus | `/dev-login?ziel=%2Fprofil` → «Petra Keller» | `/keine-saison?ziel=%2Fprofil` mit Hinweis |
| K2.5 Nochmals ohne Saison | Auf dem Hinweis «Nochmals versuchen» | Bleibt auf dem Hinweis, keine Fehlermeldung |
| K2.6 Nochmals nach Reset | `POST /api/test/reset`, neu anmelden, «Nochmals versuchen» | Zurück zu `/teams` bzw. `/`, Inhalt erscheint |
| K2.7 Abmelden | «Abmelden» | `/dev-login`, Sitzung beendet (`GET /api/sitzung` ohne Person) |
| K2.8 Neuladen | Auf `/keine-saison?ziel=%2Fteams` neu laden | Hinweis bleibt, keine weiteren Fehler |
| K2.9 Zurück-Taste | Nach Weiterleitung Zurück drücken | Kein leerer Bildschirm, keine Endlosschleife |
| K2.10 Doppelklick | Doppelklick auf «Nochmals versuchen» und auf «Abmelden» | Kein Fehler, kein doppelter Ablauf mit Fehlermeldung |
| K2.11 Ohne Anmeldung | Ohne Sitzung `/teams` öffnen | Anmeldung (`/dev-login?ziel=%2Fteams`), nicht der Saison-Hinweis |
| K2.12 Darkmode | K2.1 mit `prefers-color-scheme: dark` | Text und Buttons gut lesbar, nichts Weisses auf Weiss |
| K2.13 Mit Saison | Seed (aktuelle Saison vorhanden), als `anna` `/` | Normale Startseite «Hallo Anna», keine Weiterleitung (Regression) |
| K2.14 Direktaufruf | Seed (aktuelle Saison vorhanden), a) ohne Anmeldung, b) als `anna`: `/keine-saison` direkt öffnen | Kein falscher Hinweis: a) Anmeldung (Zugriff «angemeldet», navigation.md), b) Weiter zum Ziel bzw. Startseite, weil es eine aktuelle Saison gibt |
| K2.15 Doppelklick Abmelden | Ohne aktuelle Saison als `anna` auf dem Hinweis Doppelklick auf «Abmelden» | Abgemeldet, kein 500 von `POST /api/abmelden` |

## Ergebnis Runde 0 (Stand `d3ea152`)

- K1.1–K1.6, K2.1–K2.13 bestanden (mobil, Desktop für K2.1/K2.3, Darkmode).
- K2.14 nicht bestanden: Hinweis «Noch keine aktuelle Saison» erscheint auch ohne Anmeldung und mit aktueller Saison.
- K2.15 nicht bestanden: zweites `POST /api/abmelden` endet mit 500 (`OptimisticLockException` in `IchResource.abmelden`). Gleich bei «Abmelden» in der Navigation auf `main`, deshalb eigene Nachbesserung.

## Nacharbeit Runde 1

- K2.14 behoben: `keineSaisonGuard` (`frontend/src/app/kern/keine-saison.guard.ts`) auf `/keine-saison`. E2E: `nb13-keine-aktuelle-saison.spec.ts`, Block «mit aktueller Saison».
- K2.15 bleibt ausserhalb dieses PRs (Nachbesserung #26).

## Ergebnis Runde 1 (Stand `0eb25ed`)

- Automatisch: Redocly-Lint ohne Befund, Backend 67 grün, E2E 74 grün (37 mobil, 37 desktop, mit axe).
- K1.1–K1.6 bestanden (409 als Problem Details für `anna` und `alex`, 401 ohne Sitzung, Laufzeit-Vertrag 200/401/409).
- K2.1–K2.14 bestanden, mobil; K2.1 und K2.3 zusätzlich Desktop; Hinweis hell (Desktop) und dunkel (mobil) gut lesbar.
- K2.14 (Befund Runde 0) behoben: Direktaufruf ohne Anmeldung → `/dev-login?ziel=%2Fteams`, mit Saison → Ziel bzw. Startseite.
- Zusatz: `?ziel=https://…` und `?ziel=//…` führen auf `/`, keine Weiterleitung nach aussen.
- Konsole: nur die erwartete Meldung «409» zu `GET /api/ich`, keine weiteren 4xx/5xx.
- K2.15 ausserhalb dieses PRs (#26).
