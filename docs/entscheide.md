# Entscheide

Protokoll der getroffenen Entscheide, neueste zuletzt. Wer eine Anforderung ändert, trägt den Entscheid hier ein.

**Entschieden** heisst: von Cyrille ausdrücklich festgelegt. **Angenommen** heisst: vorgeschlagen und nicht widersprochen.

## 15.11.2026 · Grundlagen

| Nr. | Entscheid | Art |
|---|---|---|
| E-01 | Eine zentrale, erweiterbare Plattform mit Kacheln. Angular im Frontend, Quarkus im Backend, PostgreSQL. Mobile first, Desktop vollwertig. | Entschieden |
| E-02 | Das Design nutzt keine Vereinsfarben, sondern ist generisch. Light- und Darkmode sind Pflicht. | Entschieden |
| E-03 | Grunddesign ist Variante 1b «Salbei frisch». | Entschieden |

## 15.11.2026 · Grundfunktionen

| Nr. | Entscheid | Art |
|---|---|---|
| E-10 | Hauptgründe für die eigene App: Trainings für mehrere Teams gleichzeitig mit teamweiser Anzeige, und Linien mit gruppierter Anzeige. | Entschieden |
| E-11 | Coaches melden sich an und ab, werden aber nicht als Spielerinnen angezeigt. | Entschieden |
| E-12 | Eine Person kann mehreren Teams angehören, mit je einer Funktion. | Entschieden |
| E-13 | Eine Spielerin gehört momentan nur einer Linie an. | Entschieden |
| E-14 | «Vielleicht» bleibt als Antwort, mit Pflichtkommentar. | Entschieden |
| E-15 | Präsenzkontrolle ist ein zukünftiges Feature, nicht für jetzt. | Entschieden |
| E-16 | Linien pro Termin werden nicht benötigt. | Entschieden |
| E-17 | Das Aufgebot für Spiele ist direkt im Kern. Wenn möglich mit Datenanbindung an swiss unihockey, sicher mit Spielort via Google-Maps-Link. | Entschieden |
| E-18 | Fahrerliste mit automatischer Fahrzeit ist ein zukünftiges Feature. | Entschieden |
| E-19 | Die App ist momentan für Erwachsene gedacht. Elternfunktionen bleiben für spätere Erweiterungen im Hinterkopf. | Entschieden |
| E-20 | Die Teams spielen in Turnierform. Ein Spieltag fasst mehrere Spiele zusammen, mit einer Anmeldung und einem Aufgebot. | Entschieden |
| E-21 | Die Coach darf alle im Team aufbieten. Der Antwortstatus steht daneben. | Entschieden |
| E-22 | Aufgebotene müssen nicht bestätigen. Wer doch nicht kann, meldet sich ab, und die Coach wird benachrichtigt. | Entschieden |
| E-23 | Linien gehören zu einem Team. Die Regel «höchstens eine Linie» gilt pro Team. | Angenommen |
| E-24 | Doppelrolle im selben Termin: eine Antwort. Die Person erscheint in jedem Team in ihrer Funktion und zählt im Gesamttotal einmal. | Angenommen |
| E-25 | Der Ausgangsstatus ist «offen». Niemand ist automatisch angemeldet. | Angenommen |
| E-26 | Goalies sind eine eigene Gruppe neben den Linien. | Angenommen |
| E-27 | Die Termintypen Training, Spieltag und Anlass gibt es von Anfang an. | Angenommen |
| E-28 | Im Kern zusätzlich: Serientermine, Antwortfrist mit Erinnerung, stellvertretende Antwort durch die Coach, Linienverwaltung, Rechte aus der Funktion, Saisons. | Angenommen |
| E-29 | Bei «nicht dabei» ist der Grund freiwillig. Kommentare sehen nur Coaches. | Angenommen |
| E-30 | Später: Gäste und Probetrainings, Warnungen, Kalender-Abo. | Angenommen |
| E-31 | swiss unihockey: Ein Admin verknüpft die Teams einmal. Spiele werden importiert und täglich abgeglichen. Eigene Daten werden nie überschrieben. Spieltage lassen sich auch von Hand erfassen. | Angenommen |

## 15.11.2026 · Repository

| Nr. | Entscheid | Art |
|---|---|---|
| E-40 | Das Repo heisst `StvBeroPlatform`. Zuerst kommen alle Anforderungen als Dateien hinein. Implementiert wird noch nichts. | Entschieden |

## 15.11.2026 · Produktionspipeline

| Nr. | Entscheid | Art |
|---|---|---|
| E-50 | Features entstehen in einer Pipeline aus drei Agenten in der Claude-Cloud: Entwicklung, Review, Test. | Entschieden |
| E-51 | Testbarkeit des Frontends für Agenten ist bei der Entwicklung wichtig. Dazu gehören API-Doku und Zustandsdiagramme. | Entschieden |
| E-52 | Die Agenten sind Claude-Cloud-Aufgaben mit GitHub-Triggern. Der Zustand steht in Labels. Die Anleitungen sind Skills im Repo. | Angenommen |
| E-53 | Gemergt wird nur von Cyrille. Nach Nacharbeit geht es immer zurück ins Review. Nach 3 Runden ist der PR blockiert. | Angenommen |
| E-54 | API contract first mit OpenAPI 3.1 in `docs/api/openapi.yaml`. | Angenommen |
| E-55 | Testbarkeit über den Accessibility-Tree (Playwright MCP), ergänzt durch `data-testid`, feste Seed-IDs, Test-Endpunkte (Reset, Uhr, Anmelden) nur in dev/test. | Angenommen |
| E-56 | Benachrichtigungen gibt es immer in der App. E-Mail oder Push kommen als Kanal dazu. | Angenommen |
| E-57 | Backend in Java 21 (LTS), Migrationen mit Flyway. Java 25 war kurz vorgesehen; Java 21 ist in der Claude-Cloud vorinstalliert, das hält die Pipeline einfacher. | Entschieden |
| E-58 | Die Agenten mergen PRs ohne Cyrilles Zutun, wenn alles in Ordnung ist. Umsetzung: Der Test-Agent mergt nach bestandenem Test per Squash-Merge, nur ohne Konflikte, mit grüner CI und genau den getesteten Stand. Das ersetzt E-53 (Merge nur durch Cyrille). | Entschieden |
| E-59 | Alle Tickets können gleichzeitig freigegeben werden. Die Pipeline arbeitet sie als Warteschlange ab: ein Feature gleichzeitig, kleinste F-Nummer mit erfüllten Voraussetzungen zuerst, nach jedem Merge stösst der Test-Agent das nächste an. | Entschieden (Ziel), Angenommen (Umsetzung) |
| E-60 | Ein PR gilt ab Runde 5 als blockiert (vorher Runde 3). Es gibt also höchstens 4 Nacharbeitsrunden nach der ersten Umsetzung. Ändert die Zahl aus E-53. | Entschieden |
| E-61 | Ein Wächter (Aufgabe mit Zeitplan, alle 2 Stunden) erkennt abgebrochene Läufe und verlorene Ereignisse, z.B. nach einem Usage-Limit, und stösst die Pipeline neu an. | Entschieden (Ziel), Angenommen (Umsetzung) |
| E-62 | Nicht blockierende Befunde aus Review und Test werden Issues mit `typ:nachbesserung` (ohne `status:bereit`). Freigegebene Nachbesserungen kommen in der Warteschlange vor dem nächsten Feature. Sicherheitsbefunde für den produktiven Betrieb sammelt F-08. | Entschieden (Ziel), Angenommen (Umsetzung) |
| E-63 | Befunde werden vor dem Merge behoben: Review-Befunde «Muss» und «Sollte» sowie Fehler und Beobachtungen aus dem Test schicken den PR zurück in die Nacharbeit. Damit ein PR fertig wird, schicken neue «Sollte»-Befunde und Beobachtungen ab Runde 3 nicht mehr zurück; sie werden wie Befunde in Code, der schon auf `main` war, Nachbesserungs-Issues mit `status:bereit` und vor dem nächsten Feature automatisch umgesetzt. Befunde aus einer Nachbesserung selbst werden nicht automatisch freigegeben (Schutz vor Endlosschleifen). «Hinweise» bleiben im Kommentar. Ersetzt E-62 teilweise. | Entschieden |
| E-64 | Push-Nachrichten an Cyrille nur, wenn etwas schiefgegangen ist (Blockade, nicht lösbarer Merge-Fehler, Wächter-Sonderfall). Den normalen Ablauf zeigt die Statusseite `status/` auf GitHub Pages, live aus der GitHub-API, mit «seit letztem Besuch». | Entschieden |

## 16.11.2026 · Darstellungen

| Nr. | Entscheid | Art |
|---|---|---|
| E-65 | Das Grunddesign ist Cyrille viel zu grün. Zusätzlich zu Hell/Dunkel/System gibt es im Profil 20 weitere Darstellungen: 10 nach verbreiteten dunklen Themes (Dracula, Nord, One Dark, Monokai, Solarized Dark, Gruvbox, Tokyo Night, Catppuccin Mocha, GitHub Dark, Night Owl) und 10 eigene (Kiesel, Düne, Lavendel, Gletscher, Beere, Graphit, Mitternacht, Aubergine, Espresso, Tiefsee). Sie ändern nur die Farben; Form, Schrift und Abstände bleiben. «Salbei frisch» bleibt vorerst Standard, bis Cyrille eine neue Standard-Darstellung wählt (O-53). Ergänzt E-02 und NF-07 um NF-08. | Entschieden (Ziel), Angenommen (Farben, Umsetzung) |
| E-66 | Zu «Düne» kommt eine dunkle Schwester «Düne Dark» dazu: derselbe warme Sandton, dunkel, mit hellem Petrol. Damit sind es 21 weitere Darstellungen. | Entschieden (Ziel), Angenommen (Farben) |
| E-67 | Nach dem Ausprobieren bleiben drei Darstellungen: «Hell» (Farben von «Salbei frisch»), «Düne» und «GitHub Dark». Alle anderen entfallen, auch das dunkle Tannengrün («Dunkel») und «Düne Dark». Ersetzt die Listen aus E-65 und E-66. Annahme: «System» bleibt wählbar und Standard, weil der Modus der Systemeinstellung folgen muss (NF-06); im Hellen zeigt es «Hell», im Dunkeln «GitHub Dark». | Entschieden (Auswahl), Angenommen («System») |

## 16.11.2026 · Batch 2

| Nr. | Entscheid | Art |
|---|---|---|
| E-68 | Das ganze Repo wird englisch: Code, Datenbank, API, Dokumente, Skills, Tests. Deutsch bleiben die Texte der Oberfläche, die Adressen im Browser (`/termine` usw.), die IDs (`ANM-03`, `E-65`), die GitHub-Labels, die Namen der Skills und die Befehle von `scripts/pipeline.sh`. Umsetzung in drei Schritten mit höchster Priorität: F-09 (Glossar, Dokumente, Pipeline), F-10 (Backend, Datenbank, API), F-11 (Frontend, E2E). Alle weiteren Features setzen F-11 voraus und sind schon englisch geschrieben. Die bestehenden Migrationen werden umgeschrieben, weil es noch keine produktiven Daten gibt. | Entschieden |
| E-69 | Coaches dürfen fast alles wie Admins, über alle Teams (Spielerinnen sehen, zuweisen, Termine, stellvertretend antworten, Kommentare sehen). Eingeschränkt ist nur das Löschen. Saisons, die Verknüpfung mit swiss unihockey und das Admin-Recht bleiben bei den Admins. Ändert RR-03, RR-04, RR-06 (F-12). | Entschieden (Ziel), Angenommen (Abgrenzung «Löschen», Kommentare, Admin-Bereiche) |
| E-70 | Die Präsenzkontrolle kommt, ist aber pro Termin bzw. Serie wählbar und standardmässig ausgeschaltet (F-14). Die Statistik zeigt Antworten und, wo erfasst, die Präsenz; Spielerinnen sehen ihre eigene (F-15). | Entschieden (wählbar, aus), Angenommen (Rest) |
| E-71 | Benachrichtigungen gehen zusätzlich per E-Mail und per Push hinaus, beides (F-19). Schliesst O-05 und BEN-10. | Entschieden |
| E-72 | Weitere Features in Batch 2: Start zeigt mindestens die nächsten zwei Trainings (F-13), Warnungen wie «kein Goalie» (F-16), Teamübersicht mit Daten von swiss unihockey, vorerst mit Simulator (F-17), Kalender-Abo (F-18), Fahrerliste mit automatisch berechneter Strecke, die der Fahrerin angerechnet wird (F-20). F-08 (Härtung) folgt nach F-11. Annahmen zur Fahrerliste: nur Kilometer, kein Geld; alle Teammitglieder dürfen fahren; Kartendienst hinter einer Schnittstelle, im Testmodus simuliert. | Entschieden (Ziel), Angenommen (Details) |
