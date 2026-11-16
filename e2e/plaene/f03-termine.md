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

## Ergänzung Test-Agent (Runde 0)

Grenzfälle aus zustaende.md (verbotene Übergänge) und Robustheit. Ausgangslage wie oben (Reset, Jetzt = Mo 16.11.2026 06:00).

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| G1 | Rechte API | `anna`: `POST /api/termine` (Team 1), `PUT /api/termine/<T01>`, `POST /api/termine/<T01>/absagen`, `PUT /api/serien/<01>`, `POST /api/serien/<01>/ausnahmen`; `petra`: `POST /api/termine` nur Team 3; `tina`: `GET /api/termine/<T01>`, `GET /api/serien/<01>` | jeweils 403 mit `regel` RR-07 |
| G2 | RR-06 | `marc` (Coach Team 2): `POST /api/termine/<T01>/absagen` `{ "grund": "Halle zu" }` | 200, T01 abgesagt |
| G3 | Abgesagt ist endgültig | `petra`: T03 nochmals absagen bzw. ändern (API); `/termine/<T03>/bearbeiten` | 409 TER-06; Seite zeigt «Dieser Termin ist abgesagt oder vorbei und lässt sich nicht mehr ändern.», im Detail kein «Termin bearbeiten» |
| G4 | Ungültige Eingaben Termin | `petra` → `/termine/neu`: Titel nur Leerzeichen; Ende 09:00 vor Beginn 10:00; Datum 10.11.2026; kein Team; Antwortfrist leer, -1, 337 | Feldfehler auf Deutsch beim Feld: «Bitte einen Titel angeben.», «Das Ende muss nach dem Beginn liegen.», «Der Termin liegt in der Vergangenheit.», «Bitte mindestens ein Team wählen.», «Bitte eine Zahl angeben.»; bei 337 eine deutsche Meldung (höchstens 336 Stunden). URL bleibt `/termine/neu` |
| G5 | Ungültige Eingaben Serie | `/serien/<01>/bearbeiten` Antwortfrist 400; API Serie länger als ein Jahr; Ausnahme ohne Angaben und mit Bis vor Von | Deutsche Feldfehler; 400 «Eine Serie dauert höchstens ein Jahr.»; «Bitte ein Datum angeben.»/«Bitte einen Grund angeben.»; «Das Ende der Ausnahme muss nach ihrem Beginn liegen.» |
| G6 | Ausnahme mit Antwort | Serie 01, Termin 08.12. bekommt eine Antwort (ab F-04 über die App, vorher direkt in der DB); Ausnahme 07.12.–13.12. «Turnier» | Liste: Di 08.12. «abgesagt · Turnier»; Detail «Abgesagt: Turnier» |
| G7 | Doppelklick | «Termin speichern», «Absagen» im Dialog je doppelt klicken | Genau ein `POST` |
| G8 | Neuladen | Nach Verschieben (K6) und Filter (K9) neu laden; Zurück-Taste nach «Vergangen» | Zustand bleibt; Zurück führt zu `?team=<T2>` |
| G9 | Serie fremder Teams | `anna` (nur Team 1) und `marc` (nur Team 2) → `/serien/<01>` | «Teams: Team 1 + Team 2» (alle Teams der Serie, nicht nur die eigenen) |
| G10 | Serie bearbeiten ohne Datenverlust | `marc` → `/serien/<01>/bearbeiten` → speichern ohne Änderung | Serie behält Team 1 + Team 2 |
| G11 | Lange Eingaben | Titel, Ort, Hinweise mit 600 Zeichen ohne Leerzeichen (z.B. ein eingefügter Link) | Gekürzt auf die Maximallänge; Detailseite mobil ohne horizontales Scrollen |
| G12 | Filter fremdes Team | `petra` → `/termine?team=<Team 3>` | Auswahl «Team» zeigt, was gefiltert ist (oder der Filter wird ignoriert); Anzeige und Liste stimmen überein |
| G13 | Dark und Desktop | `/termine`, `/termine/<T03>`, `/termine/neu?art=serie`, `/serien/<01>`, Dialog «Termin absagen» dunkel, 375 und 1280 | Gut lesbar, nichts Weisses auf Weiss |

## Nacharbeit Runde 1

Automatisiert in `e2e/tests/f03-termine.spec.ts` (Abschnitt «Befunde aus dem Test») und `backend/.../TermineTest.java`.

| Befund | Fall | Test |
|---|---|---|
| Fehler 1 / G9 | `anna`, `marc` → `/serien/<01>` zeigt «Team 1 + Team 2» | E2E «Serie nennt alle ihre Teams», `ter11SerieNenntAlleTeamsAuchFremde` |
| Beobachtung 1 / G4, G5 | Antwortfrist 337 bzw. 400 und -1 im Formular (Termin und Serie), API 337 | E2E «Antwortfrist ausserhalb 0 bis 336 Stunden», `ter10AntwortfristHoechstensZweiWochenMitDeutscherMeldung` |
| Beobachtung 2 / G11 | Langer Titel, Ort, Hinweise ohne Leerzeichen, Detailseite | E2E «Lange Wörter … brechen um» (mobil und desktop) |
| Beobachtung 3 / G12 | `petra` → `/termine?team=<Team 3>`: Auswahl «Team 3» | E2E K9 (Ende) |

## Nachtest Runde 1 (Test-Agent)

Ausgangslage wie oben. Prüft die Befunde aus Runde 0 erneut und die Kriterien K1 bis K9 als Regression.

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| R1 | Serie fremder Teams (G9) | `anna`, `marc` → `/serien/<01>`; `tina` → `/serien/<01>` | anna und marc: «Teams: Team 1 + Team 2». tina: «Keine Berechtigung», API 403 |
| R2 | Antwortfrist im Formular (G4, G5) | `petra` → `/termine/neu`, Antwortfrist 337, -1, 2.5, leer → «Termin speichern»; `/serien/<01>/bearbeiten` 400 | «Höchstens 336 Stunden (14 Tage).», «Mindestens 0 Stunden.», «Bitte eine ganze Zahl angeben.»; Feld `aria-invalid="true"` und fokussiert; kein `POST` |
| R3 | Antwortfrist und Pflichtfelder API | `POST /api/termine` mit `antwortfristStunden` 337 bzw. -1, leerem Titel, Hinweise 501 Zeichen, `teamIds: []` | 400, `felder[].meldung` auf Deutsch |
| R4 | Lange Wörter (G11) | Termin mit Titel, Ort, Hinweisen aus einem 320 Zeichen langen Link ohne Leerzeichen; Detail, Liste, Bearbeiten bei 375 px | `scrollWidth` 375, Text bricht um, Titel auf 80 Zeichen gekürzt |
| R5 | Filter fremdes Team (G12) | `petra` → `/termine?team=<Team 3>` mobil und Desktop | Auswahl zeigt «Team 3», Liste nur Teamessen |
| R6 | Regression K1–K9 | wie K1–K9, mobil und Desktop; Doppelklick auf «Termin speichern» und «Absagen» | wie oben; je genau ein `POST` |
| R7 | Darkmode | `/serien/<01>` (anna, mobil), Feldfehler Antwortfrist (mobil), `/termine?team=<Team 3>` (Desktop) | gut lesbar, Fehlertext erkennbar |
