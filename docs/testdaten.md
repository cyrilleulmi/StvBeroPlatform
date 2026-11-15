# Testdaten (Seed)

Der Seed ist der feste Ausgangszustand für Entwicklung, automatische Tests und den Test-Agenten. `POST /api/test/reset` stellt genau diesen Zustand her.

Die Daten sind so gewählt, dass die Kernfälle sichtbar werden: gemeinsames Training zweier Teams, Doppelrollen, Linien, Goalies, «Ohne Linie», alle vier Antwortstatus, Spieltag mit Aufgebot.

**Regeln**

- IDs sind fest. Tests und der Test-Agent dürfen sie verwenden, z.B. für Deep Links.
- Wer den Seed ändert, passt diese Datei im selben PR an. Bestehende IDs und Zählerwerte ändern sich nur mit gutem Grund, weil Tests darauf bauen.
- Namen sind erfunden.

## Uhr

| | |
|---|---|
| Standard-«Jetzt» nach Reset | **Montag, 16.11.2026, 06:00** (Europe/Zurich) |
| Zeitzone | Europe/Zurich |
| Standard-Antwortfrist | 24 Stunden vor Beginn (Annahme zu O-11) |
| Erinnerung | 12 Stunden vor der Antwortfrist (Annahme) |

## ID-Schema

Alle IDs sind UUIDs. Die erste Ziffer zeigt den Typ, die letzten zwei die Nummer.

| Typ | Muster |
|---|---|
| Team | `10000000-0000-4000-8000-0000000000NN` |
| Person | `20000000-0000-4000-8000-0000000000NN` |
| Gruppe (Linie, Goalies) | `30000000-0000-4000-8000-0000000000NN` |
| Termin | `40000000-0000-4000-8000-0000000000NN` |
| Spiel | `50000000-0000-4000-8000-0000000000NN` |
| Saison | `60000000-0000-4000-8000-0000000000NN` |
| Serie | `70000000-0000-4000-8000-0000000000NN` |
| Konto | `80000000-0000-4000-8000-0000000000NN` (gleiche Nummer wie die Person) |

## Stand des Seeds

Der Seed wächst mit den Features. Was heute im Seed ist:

| Feature | Inhalt |
|---|---|
| F-00 | Saisons, alle Personen mit Konto und Schlüssel, Uhr auf Standard-«Jetzt». E-Mail jeder Person: `<schluessel>@example.ch` |
| F-01 | Teams 01–03 (Saison 2026/27), Team 01 zusätzlich in Saison 2025/26, Teamzugehörigkeiten mit Funktion gemäss Tabelle «Personen», noch **ohne Linien** (alle Spielerinnen «ohne Linie»). Vera: Spielerin in Team 1, nur Saison 2025/26. Alex ist in keinem Team. Der Teamname gilt pro Saison: Wird Team 1 umbenannt, zeigt 2025/26 weiterhin «Team 1» (SAI-02, Nachbesserung #21) |

Gruppen, Linienzuweisungen, Serien, Termine und Antworten folgen mit F-02 bis F-05. Die Tabellen unten sind das Zielbild.

Im Profil `dev` wird der Seed beim Start des Backends geladen. Jeder Neustart (auch Live-Reload) setzt die Daten zurück.

## Saisons

| Nr. | Saison | Zeitraum | Status |
|---|---|---|---|
| 01 | 2025/26 | 01.07.2025–30.06.2026 | Archiv |
| 02 | 2026/27 | 01.07.2026–30.06.2027 | **aktuell** |

## Teams (Saison 2026/27)

| Nr. | Team | swiss unihockey |
|---|---|---|
| 01 | Team 1 | verknüpft mit Team-ID `900001` (simuliert) |
| 02 | Team 2 | nicht verknüpft |
| 03 | Team 3 | nicht verknüpft |

## Gruppen

| Nr. | Team | Gruppe | Reihenfolge |
|---|---|---|---|
| 01 | Team 1 | Linie 1 | 1 |
| 02 | Team 1 | Linie 2 | 2 |
| 03 | Team 1 | Goalies | – |
| 04 | Team 2 | Linie 1 | 1 |
| 05 | Team 2 | Linie 2 | 2 |
| 06 | Team 2 | Goalies | – |
| 07 | Team 3 | Linie 1 | 1 |
| 08 | Team 3 | Goalies (leer) | – |

## Personen

Der **Schlüssel** wird für `/dev-login` und `POST /api/test/anmelden` verwendet.

| Nr. | Schlüssel | Name | Team 1 | Team 2 | Team 3 | Admin |
|---|---|---|---|---|---|---|
| 01 | `alex` | Alex Admin | – | – | – | ✓ |
| 02 | `petra` | Petra Keller | **Coach** | Spielerin · Linie 1 | – | – |
| 03 | `marc` | Marc Huber | – | **Coach** | – | – |
| 04 | `ruth` | Ruth Meier | – | – | **Coach** | – |
| 05 | `anna` | Anna Muster | Spielerin · Linie 1 | – | – | – |
| 06 | `lea` | Lea Graf | Spielerin · Linie 1 | Spielerin · Linie 2 | – | – |
| 07 | `mia` | Mia Frei | Spielerin · Linie 1 | – | – | – |
| 08 | `sara` | Sara Brun | Spielerin · Linie 2 | – | – | – |
| 09 | `nina` | Nina Roth | Spielerin · Linie 2 | – | – | – |
| 10 | `jana` | Jana Wyss | Spielerin · Goalies | – | – | – |
| 11 | `kim` | Kim Baumann | Spielerin · ohne Linie | – | – | – |
| 12 | `eva` | Eva Steiner | – | Spielerin · Linie 1 | – | – |
| 13 | `lara` | Lara Suter | – | Spielerin · Linie 1 | – | – |
| 14 | `zoe` | Zoe Kaufmann | – | Spielerin · Linie 2 | – | – |
| 15 | `ella` | Ella Fischer | – | Spielerin · Goalies | – | – |
| 16 | `tina` | Tina Bucher | – | – | Spielerin · Linie 1 | – |
| 17 | `vera` | Vera Studer | nur Saison 2025/26: Spielerin | – | – | – |

Besonderheiten:

- **Petra** hat eine Doppelrolle: Coach in Team 1, Spielerin in Team 2.
- **Lea** ist in zwei Teams Spielerin, mit unterschiedlicher Linie.
- **Kim** ist keiner Linie zugewiesen.
- **Vera** gibt es nur im Archiv.

## Serien

| Nr. | Serie | Teams | Zeitraum | Ausnahme |
|---|---|---|---|---|
| 01 | Training Di 18:30–20:00, Sporthalle Nord | Team 1 + Team 2 | 18.08.2026–27.04.2027 | Weihnachtsferien 21.12.2026–03.01.2027 |
| 02 | Training Do 19:00–20:30, Sporthalle Nord | Team 1 | 20.08.2026–29.04.2027 | – |
| 03 | Training Mi 18:00–19:30, Turnhalle Dorf | Team 3 | 19.08.2026–28.04.2027 | – |

## Termine mit festen IDs

Die Serien erzeugen weitere Termine. Diese hier haben feste IDs und definierte Antworten.

| Nr. | Termin | Teams | Besonderes |
|---|---|---|---|
| 01 | Training Di 17.11.2026 18:30 (Serie 01) | Team 1 + Team 2 | **Hauptbeispiel**, alle Antwortstatus |
| 02 | Training Do 19.11.2026 19:00 (Serie 02) | Team 1 | Nur Anna hat geantwortet |
| 03 | Training Di 24.11.2026 18:30 (Serie 01) | Team 1 + Team 2 | **abgesagt** («Halle besetzt») |
| 04 | Spieltag So 22.11.2026 | Team 1 | Aus swiss unihockey, 2 Spiele, Aufgebot im **Entwurf** |
| 05 | Spieltag Sa 28.11.2026 | Team 2 | Von Hand erfasst, 1 Spiel, Aufgebot **veröffentlicht** |
| 06 | Anlass Fr 11.12.2026 19:00 Teamessen | Team 1 + 2 + 3 | Niemand hat geantwortet |
| 07 | Training Di 10.11.2026 18:30 (Serie 01) | Team 1 + Team 2 | Vergangen |
| 08 | Training Di 12.05.2026 18:30 | Team 1 (Saison 2025/26) | Archiv, mit Vera |

### Termin 01: Training Di 17.11.2026, Team 1 + Team 2

Antworten:

| Person | Antwort | Kommentar |
|---|---|---|
| Anna | dabei | |
| Lea | dabei | |
| Mia | nicht dabei | «Krank» |
| Sara | dabei | |
| Nina | offen | |
| Jana | dabei | |
| Kim | dabei | |
| Petra | dabei | |
| Eva | vielleicht | «Komme evtl. später» |
| Lara | dabei | |
| Zoe | nicht dabei | |
| Ella | offen | |
| Marc | nicht dabei | «Ferien» |

**Erwartete Übersicht** (das ist der wichtigste Prüfpunkt der App):

```
Training Di 17.11. 18:30 · Team 1 + Team 2     7 dabei · 2 nicht dabei · 1 vielleicht · 2 offen

Team 1                                         5 dabei · 1 nicht dabei · 0 vielleicht · 1 offen
  Linie 1      Anna ✓   Lea ✓   Mia ✗
  Linie 2      Sara ✓   Nina ?
  Goalies      Jana ✓
  Ohne Linie   Kim ✓
Team 2                                         3 dabei · 1 nicht dabei · 1 vielleicht · 1 offen
  Linie 1      Petra ✓  Eva ~   Lara ✓
  Linie 2      Lea ✓    Zoe ✗
  Goalies      Ella ?

Coaches        Petra ✓ (Team 1)   Marc ✗ (Team 2)
```

- Gesamt: 12 verschiedene Spielerinnen. Lea zählt in beiden Teams, im Gesamttotal einmal ([ANM-32](anforderungen/04-an-und-abmeldung.md)).
- Petra erscheint im Coach-Block (Team 1) und als Spielerin in Team 2, mit derselben Antwort ([ANM-30, ANM-31](anforderungen/04-an-und-abmeldung.md)).
- Coaches zählen nicht zu den Spielerinnen ([PTF-07](anforderungen/01-personen-teams-funktionen.md)).
- Innerhalb einer Gruppe sind Personen nach Nachname sortiert.

### Termin 04: Spieltag So 22.11.2026, Team 1

| | |
|---|---|
| Quelle | swiss unihockey (simuliert) |
| Spielort | Sporthalle Seefeld, Musterdorf, mit Google-Maps-Link |
| Treffpunkt | 08:00, Parkplatz Sporthalle Nord |
| Hinweise | «Weisse Trikots mitnehmen» |
| Spiel 01 | 09:30 · Team 1 – UHC Musterdorf (Heim) |
| Spiel 02 | 11:45 · Unihockey Beispielstadt – Team 1 (Auswärts) |

Antworten: Anna dabei, Lea dabei, Mia vielleicht («Muss evtl. arbeiten»), Sara dabei, Nina offen, Jana dabei, Kim nicht dabei, Petra (Coach) dabei.

Aufgebot im Entwurf: Anna, Lea, Mia, Sara, Jana.

### Termin 05: Spieltag Sa 28.11.2026, Team 2

| | |
|---|---|
| Quelle | von Hand erfasst |
| Spielort | Turnhalle Weid, Beispielstadt, mit Google-Maps-Link |
| Treffpunkt | 12:30, Sporthalle Nord |
| Spiel 03 | 14:00 · Team 2 – UH Testingen (Heim) |

Antworten: Petra dabei, Lea dabei, Eva dabei, Lara dabei, Zoe nicht dabei, Ella dabei, Marc (Coach) dabei.

Aufgebot veröffentlicht: Petra, Eva, Lara, Ella. Lea ist «nicht aufgeboten».

## Simulierte swiss-unihockey-Daten

Im Profil `dev`/`test` spricht die App nicht mit swiss unihockey, sondern mit einem Simulator. Er liefert nach Reset für Team-ID `900001` genau Spiel 01 und Spiel 02 von Termin 04. Mit `PUT /api/test/swiss-unihockey/spiele` lassen sich andere Daten vorgeben.

## Szenarien für Tests

Jedes Szenario beginnt mit `POST /api/test/reset`.

| Nr. | Szenario | Schritte | Erwartung | Bezug |
|---|---|---|---|---|
| S1 | Übersicht gemeinsames Training | Als `petra` Termin 01 öffnen | Übersicht genau wie oben | ANM-20–32 |
| S2 | Antwort mit einem Tap | Als `nina` in der Terminliste bei Termin 01 «dabei» tippen | Antwort gespeichert, Team 1 zeigt 6 dabei, 0 offen | ANM-08 |
| S3 | Vielleicht ohne Kommentar | Als `nina` bei Termin 01 «vielleicht» wählen, Kommentar leer lassen | Speichern nicht möglich, Fehlermeldung beim Feld | ANM-04 |
| S4 | Kommentare nur für Coaches | Als `anna` Termin 01 öffnen | Kein Kommentar sichtbar. Als `petra`: «Krank» bei Mia sichtbar | ANM-07 |
| S5 | Kurzfristige Absage | Uhr auf 16.11.2026 19:00. Als `anna` bei Termin 01 auf «nicht dabei» wechseln | Petra (Coach Team 1) wird benachrichtigt, Marc nicht (Annahme O-17) | ANM-10, BEN-02 |
| S6 | Erinnerung | Uhr auf 16.11.2026 06:31 | Nina und Ella erhalten eine Erinnerung zu Termin 01, sonst niemand | BEN-01, BEN-13 |
| S7 | Stellvertretend antworten | Als `petra` für Nina bei Termin 01 «nicht dabei» eintragen | Gespeichert, bei Nina ist «eingetragen von Petra Keller» sichtbar | ANM-11, ANM-12 |
| S8 | Kein Stellvertreten für fremde Teams | Als `marc` bei Termin 01 für Anna antworten wollen | Aktion nicht angeboten, API antwortet 403 | RR-06 |
| S9 | Aufgebot veröffentlichen | Als `petra` Termin 04 → Aufgebot → veröffentlichen | Alle 7 Spielerinnen von Team 1 werden benachrichtigt. Anna sieht «aufgeboten», Kim «nicht aufgeboten» | AUF-23, AUF-24 |
| S10 | Aufgebotene meldet sich ab | Als `eva` bei Termin 05 auf «nicht dabei» wechseln | Marc wird benachrichtigt | AUF-26, BEN-04 |
| S11 | Aufgebot nur für Coaches | Als `anna` `/termine/40000000-0000-4000-8000-000000000004/aufgebot` öffnen | Hinweis «Keine Berechtigung», API 403 | RR-03 |
| S12 | Verschiebung durch swiss unihockey | Simulator: Spiel 02 auf 12:15. Abgleich auslösen | Spiel 02 zeigt 12:15, Treffpunkt und Aufgebot unverändert, Team 1 wird benachrichtigt | SUH-05, SUH-06 |
| S13 | Abgesagter Termin | Als `anna` Termin 03 öffnen | Als abgesagt erkennbar, mit Grund. Antworten nicht möglich | TER-06 |
| S14 | Linie wechseln | Als `petra` in Team 1 Kim der Linie 2 zuweisen | Kim erscheint bei Termin 01 unter Linie 2 von Team 1, «Ohne Linie» ist leer und wird nicht angezeigt | LIN-07, LIN-06 |
| S15 | Eine Linie pro Team | Als `petra` Anna zusätzlich Linie 2 in Team 1 zuweisen | Anna wechselt in Linie 2, ist danach nicht mehr in Linie 1 | LIN-02 |
| S16 | Archiv | Als `alex` unter `/verwaltung/saisons` bei 2025/26 «Teams ansehen», Team 1 öffnen | Hinweis «Archiv: nur lesend», Vera als Spielerin sichtbar, keine Bearbeitung möglich (API 409). Termin 08 ab F-03 | SAI-02 |
| S17 | Anmeldung im Testmodus | `/profil` ohne Anmeldung öffnen, auf `/dev-login` «Petra Keller» wählen | Weiterleitung auf `/dev-login?ziel=%2Fprofil`, danach zurück auf `/profil` | F-00 |
| S18 | Kacheln nach Rolle | Als `anna` und als `alex` `/` öffnen | Beide sehen die Kachel «Anwesenheit». Nur `alex` hat «Verwaltung» in der Navigation | NF-12, RR-05 |
| S20 | Doppelrolle | Als `petra` `/teams` öffnen | Team 1 «Coach», Team 2 «Spielerin». In Team 2 steht Petra bei den Spielerinnen, in Team 1 bei den Coaches | PTF-04, PTF-07, PTF-12 |
| S21 | Neue Saison | Als `alex` unter `/verwaltung/saisons` 2027/28 mit Übernahme aus 2026/27 anlegen, zur aktuellen machen | Teams und Zugehörigkeiten übernommen, 2026/27 wird Archiv | SAI-04, SAI-06 |
| S22 | Eine Funktion pro Team | Als `alex` in Team 1 Petra nochmals aufnehmen wollen | Oberfläche: «(schon im Team)», nicht wählbar. API: 409 mit Regel PTF-04 | PTF-04 |
| S23 | Keine aktuelle Saison | `DELETE /api/test/aktuelle-saison`, als `anna` `/teams` öffnen | Weiterleitung auf `/keine-saison?ziel=%2Fteams` mit Hinweis «Noch keine aktuelle Saison», `GET /api/ich` antwortet 409 mit Regel SAI-03 | SAI-03 |
| S19 | Darstellung | Als `anna` unter `/profil` «Dunkel» wählen, neu laden | Bleibt dunkel. «System» folgt der Systemeinstellung | NF-06 |
