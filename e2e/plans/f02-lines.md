# Testplan F-02 · Linien

Issue #3 · Anforderungen LIN-01 bis LIN-12, RR-03, RR-07 · Tests: `../tests/f02-linien.spec.ts`

Allgemein für jeden Fall:

- Vorbereitung: `POST /api/test/reset`, dann `POST /api/test/anmelden` mit `{ "schluessel": "<person>" }`.
- Ansichten: mobil 375×812 und Desktop 1280×800, hell und dunkel. Drag & Drop nur Desktop; mobil über das Auswahlfeld.
- Nach jedem Fall: keine Konsolenfehler.

IDs: Team 1 `10000000-0000-4000-8000-000000000001`, Team 2 `…002`. Gruppen `30000000-0000-4000-8000-0000000000NN`
(01 T1 Linie 1, 02 T1 Linie 2, 03 T1 Goalies, 04 T2 Linie 1, 05 T2 Linie 2, 06 T2 Goalies, 07 T3 Linie 1, 08 T3 Goalies).

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| K1 | Seed | `petra` → `/teams/<T1>/linien` | Reihenfolge Linie 1 (Mia Frei, Lea Graf, Anna Muster), Linie 2 (Sara Brun, Nina Roth), «Linie hinzufügen», Goalies (Jana Wyss), Ohne Linie (Kim Baumann). `/teams/<T1>`: Listen «Linie 1», «Linie 2», «Goalies», «Ohne Linie», Link «Linien einteilen» |
| K2.1 | Hinzufügen | «Linie hinzufügen» | Status «Linie 3 ist hinzugefügt.», Linie 3 vor den Goalies |
| K2.2 | Umbenennen | «Linie 3 umbenennen» → «Powerplay» → «Speichern»; leerer Name | «Neuer Name: Powerplay.»; leer: Feldfehler «Bitte einen Namen eingeben.» |
| K2.3 | Reihenfolge | «Powerplay nach oben» zweimal | Powerplay zuoberst; «Linie 1 nach oben» war gesperrt |
| K2.4 | Löschen | «Linie 2 löschen» → Dialog «Linie 2 löschen?» → «Abbrechen», dann «Löschen» | Abbrechen ändert nichts. Löschen: Sara und Nina unter «Ohne Linie» (S24) |
| K3.1 | Auswahlfeld | `zuweisung-<Kim>` → «Linie 2» (S14) | Status «Kim Baumann ist jetzt in Linie 2.», sofort gespeichert (nach Neuladen gleich), Fokus bleibt im Feld |
| K3.2 | Drag & Drop | Anna am Griff auf «Goalies» ziehen | Status «Anna Muster ist jetzt in Goalies.», nach Neuladen gleich |
| K4 | Eine Gruppe pro Team | Anna → «Linie 2» (S15) | Anna nur noch in Linie 2 |
| K5 | Zwei Teams | `alex`: Lea in T1 Linie 1, T2 Linie 2; Lea in T1 → Goalies | T2 unverändert, Auswahl in T2 zeigt nur Gruppen von T2 |
| K6 | Goalies | Goalies ohne Buttons; API `DELETE …/gruppen/<03>` | 409, Regel LIN-05 (S25) |
| K7 | Rechte | `anna` → `/teams/<T1>/linien`; `petra` → `/teams/<T2>/linien`; `marc` → `/teams/<T1>/linien` | Anna und Petra (in T2 Spielerin): Hinweis «Nur Coaches des Teams können die Einteilung ändern.», keine Bedienelemente. Marc: «Keine Berechtigung», API 403 (S26) |

## Ergänzung Test-Agent (Runde 0)

Grenzfälle aus states.md («Linien») und Robustheit. Vorbereitung wie oben.

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| G1 | Name doppelt (LIN-09), E2E `F-02 G1` | `petra`, mobil → «Linie hinzufügen» → «Linie 3 umbenennen» → «Linie 1» (auch «LINIE 1», «goalies») → «Speichern» | 409 Regel LIN-09; Meldung «In diesem Team gibt es schon eine Gruppe «Linie 1».» **sichtbar beim Namensfeld**, Fokus bleibt im Feld, Name unverändert |
| G2 | Name zu lang | API `POST …/gruppen` mit 300 Zeichen; Feld «Name von …» | 400 mit Feldfehler; Feld hat `maxlength="30"` |
| G3 | Goalies unveränderlich (S25) | API `PUT …/gruppen/<03>` `{ name: "X" }`, `DELETE …/gruppen/<03>` | je 409, Regel LIN-05 |
| G4 | Coach nicht zuweisbar (LIN-10) | `petra` API `PUT /api/teams/<T1>/zuweisungen/<Petra>` `{ gruppeId: <01> }` | 409, Regel LIN-10 |
| G5 | Gruppe eines anderen Teams | API `PUT /api/teams/<T1>/zuweisungen/<Lea>` `{ gruppeId: <05> }` | 404 |
| G6 | Schreibrechte | `anna` und `marc`: `POST …/gruppen` `{ name: "Test" }`, `PUT …/gruppen/<01>`, `DELETE …/gruppen/<01>`, `PUT …/zuweisungen/<Lea>` | je 403; `GET …/gruppen` anna 200, marc 403 |
| G7 | Fokus beim Verschieben, E2E `F-02 G7` | Tastatur: Fokus auf «Linie 2 nach unten» (mit Linie 3), Enter | Linie 2 ist zuunterst. Fokus bleibt auf einem Bedienelement dieser Linie (z.B. «Linie 2 nach oben»), nicht auf `body` |
| G8 | Doppelklick | «Linie hinzufügen» doppelt; «Löschen» im Dialog doppelt; «Speichern» beim Umbenennen doppelt | Genau eine Linie neu bzw. eine Löschung bzw. eine Änderung, keine Fehlermeldung |
| G9 | Zurück/Vor | Nach «Linie hinzufügen» Zurück-Taste, dann Vor | Zustand vom Server, Linie 3 bleibt |
| G10 | Archiv | `alex` → `/teams/<T1>?saison=<2025/26>` | Hinweis Archiv, «Ohne Linie: Vera Studer», kein Link «Linien einteilen» |
| G11 | Darkmode | `/teams/<T1>/linien` mit Umbenennen-Feldfehler und Lösch-Dialog, `/teams/<T1>` | Alles lesbar, Kontrast ausreichend |

## Nachtest Test-Agent (Runde 1)

Geprüft auf `ec9c568`, Vorbereitung wie oben.

| Nr. | Fall | Schritte | Erwartung |
|---|---|---|---|
| R1.1 | G1 mobil | `petra`, 375×812 → «Linie hinzufügen» → «Linie 3 umbenennen» → «LINIE 1», dann «goalies» (mit Enter) → «Speichern» | Meldung beim Feld (`aria-invalid`, `aria-describedby`) im sichtbaren Bereich, Fokus im Feld, keine Meldung oben auf der Seite |
| R1.2 | G1 Korrektur | nach R1.1 «Powerplay» → «Speichern», Neuladen | «Neuer Name: Powerplay.», Feldfehler weg, Fokus auf «Powerplay umbenennen», Name bleibt nach Neuladen |
| R1.3 | G1 Abbrechen | nach Fehler «Abbrechen», erneut «Linie 3 umbenennen» | Feld mit «Linie 3», ohne alten Fehler; Fokus nach Abbrechen auf «Linie 3 umbenennen» |
| R1.4 | G1 Doppelklick | Desktop dunkel, «Linie 2» → Doppelklick «Speichern» | nur eine Anfrage (409), Meldung beim Feld gut lesbar |
| R1.5 | G7 Tastatur | Enter auf «Linie 2 nach unten» (zuunterst); Enter auf «Linie 3 nach oben» (zuoberst), Enter, Leertaste | Fokus wechselt am Rand auf den Gegenknopf derselben Linie, in der Mitte bleibt er; nie auf `body` |
| R1.6 | Rechte (Regression) | API `GET`/`PUT …/gruppen/<01>`, `DELETE …/gruppen/<03>` als `anna`, `marc`, `petra` | anna 200/403/403, marc 403/403/403, petra 200/200/409 |
