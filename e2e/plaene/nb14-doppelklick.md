# Testplan Nachbesserung #14 · Doppelklick sperren (schreibende Aktionen)

Issue #14 · PR #27 · Regel «Formulare» in `docs/zustaende.md`, Zustand «Aktion läuft» in `docs/testbarkeit.md`

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`, dann anmelden über `/dev-login` oder `POST /api/test/anmelden`
  mit `{ "schluessel": "<person>" }`. Uhr: Standard, spielt keine Rolle.
- Damit der zweite Klick sicher in die laufende Anfrage fällt, wird die Antwort der geprüften
  Anfrage zurückgehalten (Playwright `page.route`, Freigabe nach den Prüfungen). Gezählt werden
  die Anfragen mit Methode und Pfad.
- «Gesperrt» heisst: Schaltfläche `disabled`. «Läuft» heisst: zusätzlich `aria-busy="true"`.
  Nach der Antwort ist `aria-busy` wieder entfernt.
- Ansichten: mobil 375×812 (alle Fälle), Desktop 1280×800 (K1.1, K2.1, K2.4). `/dev-login`
  einmal im Darkmode, während eine Anmeldung läuft.
- Nach jedem Fall: Konsolenfehler und unerwartete 4xx/5xx prüfen.

## K1 · Anmelden unter `/dev-login` (Kriterium 1)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K1.1 Doppelklick | Ohne Sitzung `/dev-login`, Antwort von `POST /api/test/anmelden` zurückhalten, Doppelklick auf «Anna Muster» | «Anna Muster» `disabled` + `aria-busy="true"`; «Petra Keller» `disabled`, ohne `aria-busy`. Nach Freigabe: `/`, «Hallo Anna». Genau 1 `POST /api/test/anmelden` |
| K1.2 Sitzung | Nach K1.1 `GET /api/sitzung` | Eine angemeldete Person: Anna |
| K1.3 Ohne Bremse | Doppelklick auf «Petra Keller» in normaler Geschwindigkeit | Genau 1 Anmeldung, Startseite «Hallo Petra», keine Konsolenfehler |
| K1.4 Fehler | Antwort von `POST /api/test/anmelden` mit 500 beantworten, Klick auf «Anna Muster» | Fehlermeldung sichtbar, danach alle Personen wieder bedienbar, kein `aria-busy`. Zweiter Versuch ohne Fehler meldet an |
| K1.5 Mit Ziel | `/dev-login?ziel=%2Fprofil`, Doppelklick «Alex Admin» | 1 Anmeldung, landet auf `/profil` |
| K1.6 Zurück-Taste | Nach K1.1 Zurück drücken | Kein Absturz, keine zweite Anmeldung |
| K1.7 Darkmode | K1.1 mit `prefers-color-scheme: dark`, Bildschirmfoto während die Anmeldung läuft | Gesperrte Personen abgeschwächt, aber lesbar; nichts Weisses auf Weiss |

## K2 · Andere schreibende Aktionen (Kriterium 2)

| Fall | Ausgangslage · Schritte | Erwartung |
|---|---|---|
| K2.1 Abmelden Profil | Als `anna` `/profil`, `POST /api/abmelden` zurückhalten, Doppelklick «Abmelden» | Schaltfläche `disabled` + `aria-busy`. Danach `/dev-login`, genau 1 `POST /api/abmelden`, kein 500 |
| K2.2 Abmelden ohne Saison | `DELETE /api/test/aktuelle-saison`, als `anna` `/teams` → `/keine-saison`, Doppelklick «Abmelden» | Wie K2.1. «Nochmals versuchen» bleibt bedienbar (keine schreibende Aktion) |
| K2.3 Umbenennen | Als `alex` `/verwaltung/teams/10000000-0000-4000-8000-000000000001`, Name ändern, `PUT /api/teams/…` zurückhalten, Doppelklick «Umbenennen» | «Umbenennen» `disabled` + `aria-busy`; «Aufnehmen» und alle «… entfernen» `disabled` ohne `aria-busy`. Genau 1 `PUT`, Name nach Neuladen geändert |
| K2.4 Entfernen | Wie K2.3, Doppelklick «Anna Muster entfernen» (`DELETE …/mitglieder/…` zurückhalten) | Nur diese Schaltfläche `aria-busy`, andere `disabled`. Genau 1 `DELETE`, kein 404/409 durch eine zweite Anfrage, Anna nicht mehr im Team |
| K2.5 Aufnehmen | Wie K2.3, Person wählen, Doppelklick «Aufnehmen» | Nur «Aufnehmen» `aria-busy`. Genau 1 `POST …/mitglieder`, kein 409 PTF-04 durch eine zweite Anfrage |
| K2.6 Funktion ändern | Wie K2.3, Funktion einer Person ändern, Anfrage zurückhalten | Auswahlfelder und Schaltflächen `disabled`, keine Schaltfläche `aria-busy`. 1 Anfrage |
| K2.7 Saison anlegen | Als `alex` `/verwaltung/saisons`, 2027/28 ausfüllen, Doppelklick «Saison anlegen» | Nur «Saison anlegen» `aria-busy`. Genau 1 `POST /api/saisons`, kein 409 durch Doppel |
| K2.8 Saison aktivieren | Wie K2.7, danach Doppelklick «Zur aktuellen Saison machen» | Nur diese Schaltfläche `aria-busy`, «Saison anlegen» `disabled` ohne `aria-busy`. 1 `POST …/aktivieren` |
| K2.9 Team anlegen | Als `alex` `/verwaltung/teams`, Name eingeben, Doppelklick «anlegen» | 1 `POST /api/teams`, Schaltfläche `aria-busy` |
| K2.10 Person speichern | Als `alex` `/verwaltung/personen/neu`, Felder ausfüllen, Doppelklick «Speichern» | 1 `POST /api/personen`, Schaltfläche `aria-busy`, keine Doppel-Person in der Liste |
| K2.11 Fehler | Eine Verwaltungsaktion mit 500 beantworten | Fehlermeldung, alle Schaltflächen wieder bedienbar, kein `aria-busy` |
| K2.12 Rollen | `anna` öffnet `/verwaltung/teams/…` | Kein Zugriff (Regression, unverändert zu F-01) |

## Regression

| Fall | Schritte | Erwartung |
|---|---|---|
| R1 Normale Bedienung | Einfacher Klick auf alle obigen Aktionen ohne Bremse | Funktioniert wie vorher, Schaltflächen danach wieder bedienbar |
| R2 Automatisch | `e2e/tests/nb14-doppelklick.spec.ts` und alle übrigen E2E-Tests | Grün, mobil und Desktop, axe ohne Befund |

## Ergebnis Runde 1 (Stand `b5962d6`)

- Automatisch: Backend 67 ✅, E2E 90 ✅ (mobil + Desktop, axe), API-Lint und Frontend-Build ✅.
- K1.1–K1.7 bestanden (mobil, Desktop für K1.1, Darkmode). Je genau 1 `POST /api/test/anmelden`.
- K2.1–K2.12 bestanden: je genau 1 Anfrage, `aria-busy` nur an der geklickten Schaltfläche,
  übrige `disabled` ohne `aria-busy`, nach Fehler wieder bedienbar. Desktop für K2.1 und K2.4.
- Konsole: nur erwartete Einträge (simulierte 500, 409 von `GET /api/ich` ohne Saison).
