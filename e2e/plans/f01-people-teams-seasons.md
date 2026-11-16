# Testplan F-01 · Personen, Teams, Saisons

Issue #2 · PR #20 · Anforderungen PTF, SAI, RR-04, RR-07

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`, dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }`.
- Uhr: Standard («Jetzt» = Mo 16.11.2026 06:00), ausser anders angegeben.
- Ansichten: mobil 375×812 (alle Fälle), Desktop 1280×800 (zentrale Fälle K2–K6).
- Nach jedem Fall: keine Konsolenfehler, keine unerwarteten 4xx/5xx.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`, Team 3 `…003`.
Saison 2025/26 `60000000-0000-4000-8000-000000000001` (Archiv), 2026/27 `…002` (aktuell).
Personen `20000000-0000-4000-8000-0000000000NN` nach test-data.md.

## K1 · Seed

| Schritt | Erwartung |
|---|---|
| Als `alex` `/verwaltung/personen` öffnen | 17 Personen, nach Nachname sortiert, Alex mit Kennzeichen «Admin» |
| `/verwaltung/teams` | Saison «2026/27 (aktuell)» gewählt, Team 1, Team 2, Team 3 |
| `/verwaltung/teams/<Team 1>` | Coaches: Petra Keller. Spielerinnen (7): Kim Baumann, Sara Brun, Mia Frei, Lea Graf, Anna Muster, Nina Roth, Jana Wyss |
| `/verwaltung/teams?saison=<2025/26>` | Hinweis `archiv-hinweis`, nur Team 1 |

## K2 · Person anlegen und bearbeiten (PTF-11)

| Fall | Schritte | Erwartung |
|---|---|---|
| K2.1 Anlegen | `alex` → `/verwaltung/personen` → «Person erfassen» → Vorname «Flurina», Nachname «Testerin», E-Mail «flurina@example.ch» → «Speichern» | Weiter auf `/verwaltung/personen/:id`, Status «Flurina Testerin ist erfasst.», in der Liste genau einmal |
| K2.2 Pflichtfelder | Formular leer speichern | «Bitte einen Vornamen eingeben.», «Bitte einen Nachnamen eingeben.» beim Feld, Felder `invalid`, kein POST |
| K2.3 E-Mail ungültig | Bei Kim E-Mail «kein-mail» speichern | «Bitte eine gültige E-Mail-Adresse eingeben.», nichts gespeichert |
| K2.4 Bearbeiten | Kim Nachname «Baumann-Huber» → Speichern → Neuladen | «Änderungen gespeichert.», nach Neuladen «Baumann-Huber» |
| K2.5 Lange Eingabe | Vorname mit 500 Zeichen eingeben | Feld nimmt höchstens 60 Zeichen. API mit 61 Zeichen: 400 «Höchstens 60 Zeichen.». Liste `/verwaltung/personen` ohne horizontales Scrollen bei 375px |
| K2.6 Doppelklick | K2.1 mit Doppelklick auf «Speichern» | Person nur **einmal** angelegt (genau ein `POST /api/personen`) |
| K2.7 Letzter Admin | Bei Alex «Admin» abwählen → Speichern | Meldung «Es braucht mindestens eine Person mit Admin-Rechten.» (409, RR-04), Häkchen bleibt nach Neuladen |

## K3 · Person mit Funktion ins Team aufnehmen (PTF-04)

| Fall | Schritte | Erwartung |
|---|---|---|
| K3.1 Aufnehmen | `alex` → `/verwaltung/teams/<Team 1>` → «Person» = Ruth Meier, «Funktion» = Coach → «Aufnehmen» | Status «Ruth Meier ist jetzt Coach in Team 1.», Ruth unter Coaches, nach Neuladen weiterhin |
| K3.2 Schon im Team (UI) | Auswahl «Person» öffnen | Mitglieder mit «(schon im Team)» und nicht wählbar |
| K3.3 Schon im Team (API) | `POST /api/teams/<Team 1>/mitglieder` mit Petra, `SPIELERIN` | 409, `regel: PTF-04` |
| K3.4 Doppelklick | K3.1 mit Doppelklick auf «Aufnehmen» | Genau eine Aufnahme, keine Fehlermeldung, kein 500 |
| K3.5 Funktion ändern | «Funktion von Anna Muster» = Coach | Anna wechselt zu den Coaches, Zähler Coaches 2 / Spielerinnen 6 |
| K3.6 Entfernen | «Mia Frei entfernen» → Neuladen | Mia nicht mehr im Team, Person besteht weiter unter `/verwaltung/personen` |

## K4 · Doppelrolle (PTF-12, S20)

| Schritte | Erwartung |
|---|---|
| `petra` → `/teams` | «Team 1 Coach», «Team 2 Spielerin» (`team-karte-<id>`) |
| `petra` → `/teams/<Team 2>` | Petra unter `team-spielerinnen` |
| `petra` → `/teams/<Team 3>` | «Keine Berechtigung», API 403 |

## K5 · Coaches und Spielerinnen getrennt (PTF-07)

| Schritte | Erwartung |
|---|---|
| `petra` → `/teams/<Team 1>` | Coaches 1: Petra Keller. Spielerinnen 7 ohne Petra |
| `petra` → `/teams/<Team 2>` | Coaches 1: Marc Huber. Spielerinnen 6 inkl. Petra, ohne Marc |
| `marc` → `/teams/<Team 1>` | «Keine Berechtigung», API 403 |

## K6 · Neue Saison mit Übernahme (SAI-04, SAI-06, S21)

| Fall | Schritte | Erwartung |
|---|---|---|
| K6.1 Anlegen | `alex` → `/verwaltung/saisons` → Vorschlag «2027/28», 01.07.2027–30.06.2028, Übernahme aus 2026/27 → «Saison anlegen» | Status «Saison 2027/28 ist angelegt …», Eintrag «geplant» |
| K6.2 Aktivieren | Bei 2027/28 «Zur aktuellen Saison machen» → Neuladen | 2027/28 «aktuell», 2026/27 und 2025/26 «Archiv» |
| K6.3 Übernahme | `/verwaltung/teams` → Team 1 | Saison 2027/28, gleiche Coaches und Spielerinnen wie 2026/27 |
| K6.4 Doppelklick | K6.1 mit Doppelklick auf «Saison anlegen» | Saison einmal angelegt, kein 500, keine Fehlermeldung |
| K6.5 Doppelter Name (API) | `POST /api/saisons` mit Name «2026/27» | 409 |

## K7 · Archiv lesend (S16, SAI-02)

| Schritte | Erwartung |
|---|---|
| `alex` → `/verwaltung/saisons` → bei 2025/26 «Teams ansehen» → Team 1 | «Saison 2025/26 im Archiv: nur lesend.», Vera Studer als Spielerin, keine Bedienelemente zum Ändern |
| API: Mitglied aufnehmen, entfernen, Team anlegen mit `?saison=<2025/26>` | jeweils 409, `regel: SAI-02` |
| `alex` → `/teams/<Team 1>?saison=<2025/26>` | Archiv-Hinweis, Vera sichtbar |

## K8 · Verwaltung für Nicht-Admins gesperrt (RR-04, RR-07)

| Schritte | Erwartung |
|---|---|
| Als `anna` und `petra` jede Route: `/verwaltung`, `/verwaltung/personen`, `/verwaltung/personen/neu`, `/verwaltung/personen/<id>`, `/verwaltung/teams`, `/verwaltung/teams/<id>`, `/verwaltung/saisons` | `role="alert"` «Keine Berechtigung», keine Formulare |
| Als `anna` und `petra`: `GET/POST /api/personen`, `PUT /api/personen/<id>`, `POST /api/teams`, `PUT /api/teams/<id>`, `POST/PUT/DELETE …/mitglieder`, `POST /api/saisons` (gültiger Inhalt), `POST /api/saisons/<id>/aktivieren` | 403 |
| Als `marc` Navigation | kein Eintrag «Verwaltung» |

## Darstellung

- Jeder neue Screen (`/teams`, `/teams/:id`, `/verwaltung`, Personen, Person, Teams verwalten, Team verwalten, Saisons) einmal im Darkmode 375px: lesbar, kein Weiss auf Weiss, kein horizontales Scrollen.
- Desktop 1280px: dieselben Screens ohne Überlauf.

## Runde 1 · Nacharbeit (Doppelklick, gleichzeitige Anfragen, Beobachtungen)

| Fall | Schritte | Erwartung |
|---|---|---|
| R1.1 Doppelklick Aktivieren | `alex` → `/verwaltung/saisons` (2027/28 angelegt) → Doppelklick «Zur aktuellen Saison machen» | Genau ein `POST …/aktivieren` 204, Status «2027/28 ist jetzt die aktuelle Saison.», kein Alarm |
| R1.2 Doppelklick Entfernen | `alex` → `/verwaltung/teams/<Team 1>` → Doppelklick «Mia Frei entfernen» | Mia entfernt, kein Alarm, keine Konsolenfehler |
| R1.3 Gleichzeitige Aufnahme (API) | 3 × gleichzeitig `POST /api/teams/<Team 1>/mitglieder` mit Ruth, `COACH` | Genau eine 201, sonst 409 `regel: PTF-04`, nie 500 |
| R1.4 Gleichzeitige Saison (API) | 3 × gleichzeitig `POST /api/saisons` mit Name «2028/29», `von` 2028-07-01, `bis` 2029-06-30 | Genau eine 201, sonst 409 `regel: SAI-01`, Saison existiert einmal |
| R1.5 «noch keinen Coach» | `alex` → `/teams/<Team 1>?saison=<2025/26>` und `/verwaltung/teams/<Team 1>?saison=<2025/26>` | «Dieses Team hat noch keinen Coach.» |
| R1.6 Langer Name | Bei Kim Vorname mit 80 × «X» eingeben → Speichern → `/verwaltung/personen` bei 375px | Feld nimmt 60 Zeichen, `scrollWidth` = 375 |
