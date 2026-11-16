# 10 · Nichtfunktionale Anforderungen

Status-Legende: siehe [README](../../README.md#status-der-anforderungen).

## Bedienung

| ID | Anforderung | Status |
|---|---|---|
| NF-01 | Die App ist ohne Anleitung nutzbar: wenige Klicks, klare Sprache, keine Fachbegriffe. | Entschieden |
| NF-02 | Die Oberfläche ist auf Deutsch. | Entschieden |
| NF-03 | Mobile first: Die App ist für das Handy in der Halle gebaut. Auf dem Desktop ist sie vollwertig nutzbar. | Entschieden |
| NF-04 | Touch-Ziele sind mindestens 44px gross. | Entschieden |
| NF-05 | Die App ist als PWA installierbar. | Entschieden |
| NF-06 | Light- und Darkmode sind Pflicht. Der Modus folgt der Systemeinstellung und lässt sich von Hand umschalten. | Entschieden |
| NF-07 | Das Grunddesign «Salbei frisch» ist für alle Kacheln verbindlich (siehe [design-principles.md](../design-principles.md)). Form, Schrift und Abstände gelten für alle Darstellungen; die Farben von «Salbei frisch» sind die Darstellung «Hell» (NF-08). | Entschieden |
| NF-08 | Es gibt drei Darstellungen: «Hell», «Düne» und «GitHub Dark» (E-67). Sie unterscheiden sich nur in den Farben und lassen sich im Profil wählen, dazu «System»: im Hellen gilt «Hell», im Dunkeln «GitHub Dark». Die Wahl bleibt im Browser gespeichert. Farben und Regeln: [design-principles.md](../design-principles.md#darstellungen). | Entschieden (Auswahl), Angenommen («System») |

## Aufbau

| ID | Anforderung | Status |
|---|---|---|
| NF-10 | Jede Funktion ist eine eigenständige Kachel. Neue Kacheln kommen dazu, ohne bestehende anzupassen. | Entschieden |
| NF-11 | Eine Kachel liefert ihre Metadaten (Titel, Icon, Route, benötigte Rolle) und registriert sich zentral. Das Dashboard rendert daraus die Übersicht. | Entschieden |
| NF-12 | Das Dashboard zeigt die Kacheln je nach Rolle. | Entschieden |

## Qualität und Lieferung

| ID | Anforderung | Status |
|---|---|---|
| NF-20 | Jedes Ergebnis ist lauffähig und direkt aufrufbar (URL bzw. Startbefehl). | Entschieden |
| NF-21 | Jedes Ergebnis ist auf Desktop und Mobile geprüft. | Entschieden |
| NF-22 | Es gibt Seed-Daten zum Ausprobieren. | Entschieden |
| NF-23 | Backend-Logik ist mit Tests abgedeckt. | Entschieden |
| NF-24 | Schema-Änderungen an der Datenbank sind als versionierte Migrationen abgelegt. | Entschieden |

## Datenschutz

| ID | Anforderung | Status |
|---|---|---|
| NF-30 | Es werden nur die Personendaten gespeichert, die für die Funktionen nötig sind. | Annahme |
| NF-31 | Kommentare und Absagegründe sind nur für Coaches und die Person selbst sichtbar ([ANM-07](04-responses.md)). | Angenommen |
| NF-32 | Welche Daten genau gespeichert werden und wer was sieht (nDSG), ist nicht abschliessend geklärt. Die App ist momentan für Erwachsene gedacht. Kommen später Minderjährige dazu, muss der Punkt neu beurteilt werden. | Offen |
