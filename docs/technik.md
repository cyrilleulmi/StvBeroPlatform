# Technik

Technologie-Entscheide und Leitplanken für die Architektur. Es ist noch nichts implementiert.

## Entschieden

| Bereich | Entscheid |
|---|---|
| Frontend | Angular in der aktuellen Version, Standalone Components, Signals. |
| Aufbau Frontend | Jede Kachel ist ein lazy geladenes Feature mit eigener Route. Das Dashboard zeigt die Kacheln je nach Rolle. |
| Backend | Quarkus mit REST-API, Java 25. |
| Aufbau Backend | Eine Package-Struktur pro Kachel mit klaren Grenzen. |
| Datenbank | PostgreSQL. Schema-Migrationen versioniert mit Flyway. |
| Rollen | Mindestens Spielerin, Coach, Helfer/Eltern, Admin. Kacheln und Aktionen sind rollenbasiert sichtbar. |
| Kachel-Konzept | Eine Kachel liefert Metadaten (Titel, Icon, Route, benötigte Rolle) und registriert sich zentral. Das Dashboard rendert daraus die Übersicht. |
| Auslieferung | Responsive Web-App, als PWA installierbar. |
| Design | Tokens als CSS Custom Properties, siehe [design-grundlagen.md](design-grundlagen.md). |

## Noch offen

| Bereich | Frage |
|---|---|
| Login | E-Mail und Passwort, Magic Link oder Keycloak/OIDC. |
| Hosting | Wo läuft die Plattform produktiv, wo die Vorschau zum Verifizieren. |
| Benachrichtigungen | E-Mail, Push über die PWA oder beides. |
| Mandantenfähigkeit | Braucht es mehrere Vereine in einer Installation. |

Alle offenen Punkte gesammelt: [offene-punkte.md](offene-punkte.md).

## Leitplanken aus den Anforderungen

Diese Punkte ergeben sich aus den fachlichen Anforderungen und sollten die Architektur von Anfang an prägen.

1. **Kern als gemeinsame Grundlage.** Personen, Teams, Funktionen, Saisons und Rollen werden von allen Kacheln gebraucht. Sie gehören in ein gemeinsames Kernmodul, nicht in die Anwesenheits-Kachel.
2. **Konto und Person trennen.** Das Login-Konto ist nicht die Person ([PTF-09](anforderungen/01-personen-teams-funktionen.md)).
3. **Rechte pro Team.** Die Berechtigung hängt von der Funktion im jeweiligen Team ab, nicht von einer globalen Rolle. Sie wird im Backend geprüft ([07](anforderungen/07-rollen-rechte.md)).
4. **Regeln statt Struktur.** «Höchstens eine Linie pro Team» ist eine prüfbare Regel, die später gelockert werden kann ([LIN-04](anforderungen/02-linien.md)).
5. **Termin zu Team ist n:m.** Ein Termin kann zu mehreren Teams gehören. Die Antwort hängt an Person und Termin, nicht am Team ([ANM-02](anforderungen/04-an-und-abmeldung.md)).
6. **Anzeige wird abgeleitet.** Die Gruppierung nach Team und Linie entsteht aus Termin, Teamzugehörigkeiten und Linien. Sie wird nicht pro Termin gespeichert.
7. **swiss unihockey hinter einer eigenen Schicht.** Die Schnittstelle liefert generische Tabellen. Eine Übersetzungsschicht macht daraus Spieltage und Spiele. Fällt die Schnittstelle aus, läuft die App weiter ([06](anforderungen/06-swiss-unihockey.md)).
8. **Eigene Daten bleiben eigene Daten.** Importierte Felder und selbst gepflegte Felder (Treffpunkt, Hinweise, Aufgebot) sind getrennt, damit der Abgleich nichts überschreibt.
9. **Benachrichtigungen mit austauschbarem Kanal.** Auslöser und Versandkanal sind getrennt ([08](anforderungen/08-benachrichtigungen.md)).
