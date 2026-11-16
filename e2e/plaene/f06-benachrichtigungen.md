# Testplan F-06 · Benachrichtigungen

Issue #7 · Anforderungen BEN-01, BEN-02, BEN-03, BEN-04, BEN-05, BEN-07, BEN-11, BEN-12, BEN-13, RR-07 ·
Tests: `../tests/f06-benachrichtigungen.spec.ts`, Backend `backend/src/test/java/ch/stvbero/platform/kern/benachrichtigung/BenachrichtigungenTest.java`, `…/kern/termin/BenachrichtigungsRegelnTest.java`

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset` (Jetzt = Mo 16.11.2026 06:00), dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }` bzw. `/dev-login`.
- Benachrichtigungen prüfen über `GET /api/test/benachrichtigungen`. Neu entstandene haben IDs, die nicht mit `90000000` beginnen (Seed 01–18).
- Ansichten: mobil 375×812, zentrale Fälle zusätzlich Desktop 1280×800; `/benachrichtigungen` und die Glocke einmal dunkel.
- Nach jedem Fall: keine Konsolenfehler, keine unerwarteten 4xx/5xx.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`. Termine T01 Training Di 17.11. 18:30 `40000000-0000-4000-8000-000000000001`,
T02 Training Do 19.11. `…002`, T03 abgesagt `…003`, T04 Spieltag Team 1 `…004` (Aufgebot Entwurf), T05 Spieltag Team 2 `…005` (Aufgebot veröffentlicht).
Personen `20000000-0000-4000-8000-0000000000NN` (Petra 02, Marc 03, Anna 05, Lea 06, Mia 07, Nina 09, Kim 11, Eva 12, Ella 15).
Benachrichtigungen `90000000-0000-4000-8000-0000000000NN` (Anna Absage T03: 02, Lea Absage T03: 03, Petra Aufgebot T05: 13, Lea Aufgebot T05: 14).

## Akzeptanzkriterien

| Nr. | Fall | Ausgangslage | Schritte | Erwartung |
|---|---|---|---|---|
| K1 | Liste und Zähler | Reset, `lea` | `/` öffnen; Glocke antippen | Glocke «Benachrichtigungen 2 ungelesen», `benachrichtigungen-zaehler` = 2. `/benachrichtigungen`, Titel «Benachrichtigungen · STV Bero», Liste «Benachrichtigungen»: zuerst Aufgebot T05 (15.11. 21:00), dann Absage T03 (14.11. 10:00); beide «Neu», `data-gelesen="false"` |
| K1b | Ohne Ungelesene | Reset, `petra`, `mia`; `alex` | `/` und `/benachrichtigungen` | Petra/Mia: kein Zähler, Einträge ohne «Neu», kein Button «Alle als gelesen markieren». Alex: «Keine Benachrichtigungen.» |
| K1c | Alle als gelesen | Reset, `lea` | `/benachrichtigungen` → «Alle als gelesen markieren»; neu laden | «Alle sind als gelesen markiert.», Zähler weg, bleibt nach Neuladen |
| K2 | S32 Klick öffnet Termin (BEN-12) | Reset, `anna` | `/benachrichtigungen` → Eintrag zu T03 antippen; Zurück | `/termine/<T03>` (abgesagt mit «Halle besetzt»), Zähler weg; zurück in der Liste ist der Eintrag gelesen, `data-gelesen="true"`, bleibt nach Neuladen |
| K2b | Fremde (RR-07) | Reset, `anna` | API `POST /api/benachrichtigungen/<Lea 03>/gelesen`; eigene zweimal | 403 bzw. 204, 204. Ohne Anmeldung 401. `GET /api/benachrichtigungen` liefert nur eigene |
| K3 | S5 kurzfristige Absage (BEN-02) | Reset, Uhr 16.11.2026 19:00, `anna` | `/termine/<T01>` → «nicht dabei»; dann «vielleicht» mit Kommentar | Genau eine neue `KURZFRISTIGE_ABSAGE` an Petra «Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet.», keine an Marc oder Anna. Weiterer Wechsel: keine zweite. Petra sieht sie in `/benachrichtigungen`, Zähler 1 |
| K4 | S6 Erinnerung (BEN-01, BEN-13) | Reset | Uhr 16.11.2026 06:31; dann 07:00 | Genau zwei `ERINNERUNG` zu T01 an Nina und Ella; nach 07:00 keine weitere. Nina sieht Zähler 1 und die Erinnerung. Vor 06:30 (Uhr 06:29) keine |
| K5 | S9 Veröffentlichen (BEN-03) | Reset, `petra` | `/termine/<T04>/aufgebot` → «Veröffentlichen» → bestätigen | 7 neue `AUFGEBOT_VEROEFFENTLICHT` an alle Spielerinnen von Team 1 (Anna, Lea, Mia, Sara, Nina, Jana, Kim); Kim und Nina «nicht aufgeboten», andere «aufgeboten». Petra nicht |
| K6 | S10 Abmeldung Aufgebotene (BEN-04) | Reset, `eva` | `/termine/<T05>` → «nicht dabei» | Marc erhält «Eva Steiner ist für Spieltag Sa 28.11. aufgeboten und hat sich abgemeldet.», Eva nicht; Marc sieht sie mit Zähler |
| K7 | Änderung Aufgebot (BEN-05) | Reset, `marc` | `/termine/<T05>/aufgebot`: Lea anhaken, Ella abhaken | Genau Lea («neu aufgeboten») und Ella («nicht mehr aufgeboten») erhalten eine Benachrichtigung, sonst niemand. Änderung im Entwurf T04 (Petra) benachrichtigt niemanden |
| K8a | Absage (BEN-07) | Reset, `petra` | `/termine/<T01>` → absagen mit Grund | Alle 12 Mitglieder von Team 1 + 2 ausser Petra erhalten `TERMIN_ABGESAGT` mit Grund |
| K8b | Verschiebung (BEN-07) | Reset, `petra` | `/termine/<T01>/bearbeiten` → Beginn 19:00 | Alle 12 ausser Petra erhalten `TERMIN_VERSCHOBEN`. Änderung nur des Orts: keine |
| K9 | Schnittstelle (BEN-11) | Code | `Kanal`, `AppKanal`, `Benachrichtigungen.senden`, Test `ben11…` | Versand über Schnittstelle, Backend-Test mit `TestKanal` grün |

## Grenzfälle

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| G1 | Rollen | `marc` sagt T01 ab (Coach nur Team 2) | Nicht angeboten bzw. 403, keine Benachrichtigung |
| G2 | Auslöser | Petra antwortet stellvertretend für Anna kurzfristig | Petra erhält keine (O-50) |
| G3 | Doppelklick | «Alle als gelesen markieren» doppelt, Eintrag doppelt antippen | keine Fehler, Zähler korrekt |
| G4 | Neuladen | `/benachrichtigungen` neu laden, Deep Link ohne Anmeldung | Weiterleitung zu `/dev-login?ziel=%2Fbenachrichtigungen`, danach zurück |
| G5 | Darstellung | Glocke mobil oben rechts und Desktop, hell und dunkel | lesbar, verdeckt nichts, Kontrast ausreichend |
| G6 | Glocke verdeckt nichts | mobil `petra` → `/termine/<T01>`, so scrollen, dass «Antwort für Mia Frei eintragen» oben rechts steht | Die Glocke liegt nicht über Bedienelementen des Inhalts; ein Tipp auf den rechten Rand des Stift-Buttons trifft den Button |
| G7 | Fokus unter Kopfzeile (O-51) | mobil `petra` → `/termine/<T01>`, ans Seitenende scrollen, mit Shift+Tab rückwärts durch alle Bedienelemente von `main` | Kein fokussiertes Element liegt unter der Kopfzeile (oben 68px) oder der Navigation unten (`scroll-padding`) |
| G8 | Kopfzeile Darkmode | mobil dunkel, `lea`, `/termine/<T01>` und `/benachrichtigungen` | Kopfzeile mit dunklem Hintergrund und Trennlinie, Glocke mit Zähler lesbar; ab 1024px keine Kopfzeile |
