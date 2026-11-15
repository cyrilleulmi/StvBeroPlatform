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
