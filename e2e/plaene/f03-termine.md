# Testplan F-03 · Termine und Serien

Issue #4 · Anforderungen TER-01 bis TER-11, RR-05, RR-06, RR-07 · Tests: `../tests/f03-termine.spec.ts`,
Backend `backend/src/test/java/ch/stvbero/platform/kern/termin/`

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset` (Jetzt = Mo 16.11.2026 06:00), dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }`.
- Ansichten: mobil 375×812 und Desktop 1280×800, hell und dunkel.
- Nach jedem Fall: keine Konsolenfehler.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`, Team 3 `…003`. Termine `40000000-0000-4000-8000-0000000000NN`,
Serien `70000000-0000-4000-8000-0000000000NN`.

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| K1 | Seed | `anna` → `/termine`; `petra` → `/serien/<01>` | Liste beginnt mit Training Di 17.11. 18:30 (T01), Do 19.11. 19:00 (T02), Spieltag So 22.11. 09:30 (T04), Di 24.11. «abgesagt · Halle besetzt» (T03). Serie 01: Jeden Dienstag 18:30–20:00, Ausnahme «Weihnachtsferien: 21.12.2026 – 03.01.2027»; keine Trainings am 22.12. und 29.12. |
| K2 | Eigene Teams | `tina` → `/termine`; `alex` → `/termine` | Tina: kein T01, erster Eintrag Mi 18.11. 18:00, T06 sichtbar. Alex (kein Team): «Keine kommenden Termine» |
| K3 | Termin für zwei Teams | `petra` → «Termin erfassen», Datum 20.11., 18:00–19:30, Team 1 + Team 2 → «Termin speichern» | Detailseite «Der Termin ist erfasst.»; erscheint bei `anna` (Team 1) und `eva` (Team 2), nicht bei `tina` |
| K3.1 | Rechte | `marc`: `POST /api/termine` nur Team 1; `anna` → `/termine/neu` | 403; Formular bietet Marc nur Team 2. Anna: «Keine Berechtigung», kein Link «Termin erfassen» |
| K4 | Serie | `petra` → `/termine/neu` → «Serie (jede Woche)», Montag 19:30–21:00, 16.11.–14.12. | `?art=serie` in der URL; Serie-Seite «Jeden Montag, 19:30–21:00»; Liste Team 1: Mo 16.11., 23.11., 30.11., 07.12., 14.12. |
| K5 | Ausnahme | `petra` → `/serien/<01>`, Ausnahme 30.11.–06.12. «Hallenreinigung» | Status «Ausnahme «Hallenreinigung» hinzugefügt.»; Di 01.12. fehlt in der Liste. Mit Antwort (ab F-04): Termin wird abgesagt, Grund «Hallenreinigung» |
| K6 | Verschieben | `petra` → T02 → «Termin bearbeiten» → Datum 20.11. | Hinweis «gilt nur für diesen Termin»; «Der Termin ist verschoben.»; Liste: Fr 20.11. 19:00 «verschoben», Do 26.11. unverändert; Serie 02 weiter «Jeden Donnerstag, 19:00–20:30» |
| K7 | Abgesagt (S13) | `anna` → T03 | «Abgesagt: Halle besetzt», «Antworten ist nicht mehr möglich.», kein «Termin bearbeiten» |
| K7.1 | Absagen | `petra` → T01 bearbeiten → «Termin absagen», ohne Grund, dann «Halle gesperrt» | Ohne Grund: Feldfehler. Danach «Der Termin ist abgesagt.», Status mit Grund |
| K8 | Antwortfrist | T01; neuer Termin 21.11. 10:00 mit 48 Stunden; Serie 01 mit 12 Stunden | «Mo 16.11. 18:30 (24 Stunden vor Beginn)»; «Do 19.11. 10:00 (48 …)»; T01 danach «Di 17.11. 06:30 (12 …)» |
| K9 | Filter | `petra` → Team «Team 2», dann «Vergangen», neu laden | URL `?team=<T2>&ansicht=vergangen`, T07 sichtbar, Auswahl bleibt nach Neuladen |
