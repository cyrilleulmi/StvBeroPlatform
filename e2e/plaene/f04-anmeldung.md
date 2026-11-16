# Testplan F-04 · An- und Abmeldung mit Übersicht

Issue #5 · Anforderungen ANM-01 bis ANM-12, ANM-20 bis ANM-25, ANM-30 bis ANM-32, RR-02, RR-03, RR-06, RR-07, TER-06 ·
Tests: `../tests/f04-anmeldung.spec.ts`, Backend `backend/src/test/java/ch/stvbero/platform/…/AntwortenTest.java`, `AntwortRegelnTest.java`

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset` (Jetzt = Mo 16.11.2026 06:00), dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }` bzw. `/dev-login`.
- Ansichten: mobil 375×812, zentrale Fälle zusätzlich Desktop 1280×800; neue Screens einmal dunkel.
- Nach jedem Fall: keine Konsolenfehler, keine unerwarteten 4xx/5xx.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`. Termine `40000000-0000-4000-8000-0000000000NN` (T01 Training Di 17.11.,
T02 Training Do 19.11., T03 abgesagt, T04 Spieltag So 22.11., T07 vergangen). Personen `20000000-0000-4000-8000-0000000000NN` (Nina 09, Anna 05, Mia 07).

## Akzeptanzkriterien

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| K1 | Seed | `GET /api/termine/<T01>/uebersicht` als `petra`; T02, T04, T05 ebenso | Antworten wie testdaten.md (T01 Tabelle, T02 nur Anna dabei, T04/T05 wie beschrieben), keine `kurzfristig`, kein `eingetragenVon` |
| K2 | S1 Übersicht T01 | `petra` → `/termine/<T01>` | Gesamt «7 dabei · 2 nicht dabei · 1 vielleicht · 2 offen». Team 1: 5/1/0/1, Linie 1 Anna ✓ Lea ✓ Mia ✗, Linie 2 Sara ✓ Nina ?, Goalies Jana ✓, Ohne Linie Kim ✓. Team 2: 3/1/1/1, Linie 1 Petra ✓ Eva ~ Lara ✓, Linie 2 Lea ✓ Zoe ✗, Goalies Ella ?. Coach-Block: Petra ✓ (Team 1), Marc ✗ (Team 2). Sortierung nach Nachname |
| K3 | S2 ein Tap | `nina` → `/termine` → bei «Training Di 17.11. 18:30» «Dabei»; dann `/termine/<T01>` → «Nicht dabei» → «Überspringen» → «Dabei» | Genau ein `PUT …/antworten/ich` pro Tap, keine Navigation; `data-antwort="dabei"`, `aria-pressed`; Zähler der Karte bzw. Übersicht Team 1 «6 dabei · 0 offen» ohne Neuladen |
| K4 | S3 vielleicht | `nina` → T01 → «Vielleicht», Kommentar leer → speichern; Kommentar nur Leerzeichen; API `PUT …/ich` `{status: VIELLEICHT}` | Dialog bleibt offen, Meldung beim Feld (`aria-invalid`, `aria-describedby`), kein bzw. abgewiesener Request; API 422 Feld `kommentar` Regel ANM-04. Mit Kommentar: gespeichert, «vielleicht» |
| K5 | nicht dabei mit Grund | `anna` → T01 → «Nicht dabei» → Grund «Arzt» → «Grund speichern»; zweiter Lauf «Überspringen» | Antwort sofort gespeichert; Grund freiwillig; Petra sieht «Arzt» bei Anna |
| K6 | S4 Kommentare | `anna` → T01; `petra` → T01; `marc` → T01; API je `GET …/uebersicht` | Anna: kein Kommentar sichtbar, Feld `kommentar` fehlt in der API. Petra: «Krank» bei Mia (Team 1). Marc: Kommentare von Team 2 (Eva «Komme evtl. später»), nicht von Mia. Mia sieht eigenen Kommentar |
| K7 | S5 kurzfristig | `PUT /api/test/uhr` 16.11.2026 19:00; `anna` → T01 → «Nicht dabei» | API `kurzfristig: true`; `petra` sieht «kurzfristig abgemeldet» bei Anna. Vor der Frist (06:00) kein `kurzfristig` |
| K8 | S7/S8 stellvertretend | `petra` → T01 → «Antwort für Nina Roth eintragen» → «nicht dabei»; `marc` → T01; API `marc` `PUT …/antworten/<Anna>`; `anna` `PUT …/antworten/<Nina>` | Nina «nicht dabei», «eingetragen von Petra Keller», auch nach Neuladen. Marc: kein Button bei Anna, API 403. Anna 403 |
| K9 | Start-Screen | `anna` → `/` | Karte `naechster-termin` «Training Di 17.11.» mit `meine-antwort`; Tap speichert; `alex` (kein Team): «Keine kommenden Termine» |
| K10 | Neuladen, abgesagt | Nach K3 neu laden; `anna` → T03; API `PUT …/T03/antworten/ich` | Antwort unverändert; T03 Buttons `disabled` mit Erklärung (aria-describedby); API 409 Regel TER-06 |

## Grenzfälle (zustaende.md) und Robustheit

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| G1 | Zurück nach OFFEN | API `PUT …/ich` `{status: OFFEN}` | 400 |
| G2 | Vergangener Termin | API `PUT …/T07/antworten/ich` als `anna` | 409 |
| G3 | Nicht beteiligt | `tina` `PUT …/T01/antworten/ich`; `alex` (Admin ohne Team) dasselbe | 403 |
| G4 | Fremde Person / Coach für Coach | `petra` `PUT …/T01/antworten/<Marc>`; `petra` für `tina` (nicht beteiligt) | 403 bzw. 404 |
| G5 | Wechsel ersetzt ganz | `mia` → T01 «Dabei» | Kommentar «Krank» weg (Petra sieht keinen Kommentar mehr) |
| G6 | kurzfristig bleibt / fällt | Uhr 19:00, `anna` «Nicht dabei» → «Vielleicht» (Kommentar) → «Dabei» | `kurzfristig` bleibt, fällt mit «dabei» |
| G7 | Selbst antworten nach Stellvertretung | nach K8 `nina` «Dabei» | «eingetragen von» fällt weg |
| G8 | Doppelklick | «Dabei» doppelt; «Grund speichern» doppelt | höchstens ein gleicher `PUT`, Endzustand korrekt |
| G9 | Lange Eingaben | Kommentar 600 Zeichen ohne Leerzeichen | gekürzt oder Meldung; mobil kein horizontales Scrollen |
| G10 | Dunkel | T01 Übersicht (petra), Dialoge, Start-Karte | gut lesbar, Statusfarben erkennbar |
