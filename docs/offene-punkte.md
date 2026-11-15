# Offene Punkte

Bis zur Klärung gelten sinnvolle Annahmen, die beim Ergebnis genannt werden.

## Plattform

| Nr. | Frage | Bemerkung |
|---|---|---|
| O-01 | **Login:** Wie melden sich Mitglieder an? | E-Mail und Passwort, Magic Link oder Keycloak/OIDC. |
| O-02 | **Hosting:** Wo läuft die Plattform produktiv, wo die Vorschau zum Verifizieren? | |
| O-03 | **Datenschutz:** Welche Daten werden gespeichert, wer sieht was (nDSG)? | Die App ist momentan für Erwachsene gedacht. Kommen Minderjährige dazu, neu beurteilen. |
| O-04 | **Mandantenfähigkeit:** Braucht es mehrere Vereine in einer Installation? | Mehrere Teams innerhalb des Vereins sind Teil des Kerns. |
| O-05 | **Benachrichtigungen:** E-Mail, Push über die PWA oder beides? | Siehe [BEN-10](anforderungen/08-benachrichtigungen.md). |
| O-06 | **Grösse:** Wie viele Teams und Mitglieder ungefähr? | |
| O-07 | ~~**Backend-Sprache:** Java oder Kotlin?~~ | Entschieden: Java 21 (E-57). |
| O-08 | ~~**Migrationen:** Flyway oder Liquibase?~~ | Entschieden: Flyway (E-57). |

## Kern

| Nr. | Frage | Bemerkung |
|---|---|---|
| O-10 | Welche Funktionen braucht es neben Spielerin und Coach? | Z.B. Assistenzcoach, Betreuerin. Siehe [PTF-06](anforderungen/01-personen-teams-funktionen.md). |
| O-11 | Wie viele Stunden vor dem Termin liegt die Antwortfrist standardmässig? | Siehe [TER-09](anforderungen/03-termine.md). |
| O-12 | Dürfen Antworten nach der Frist noch geändert werden? | Annahme: ja, mit Meldung an die Coach ([ANM-09](anforderungen/04-an-und-abmeldung.md)). |
| O-13 | Sehen Spielerinnen den Status der anderen? | Annahme: ja, aber keine Kommentare ([ANM-25](anforderungen/04-an-und-abmeldung.md)). |
| O-14 | Gehört ein Spieltag immer zu genau einem Team? | Annahme: ja ([AUF-03](anforderungen/05-spieltage-aufgebot.md)). |
| O-15 | Heisst es in der Oberfläche «Spielerin» oder «Spieler/in»? | In der Erarbeitung wurde «Spielerinnen» verwendet, in älteren Unterlagen «Spieler/innen». |
| O-16 | Farben und Symbole für die vier Antwortstatus | Vorschlag in [design-grundlagen.md](design-grundlagen.md#noch-nicht-festgelegt). |
| O-17 | Wer wird bei einer kurzfristigen Absage benachrichtigt, wenn ein Termin mehrere Teams hat? | Annahme: nur die Coaches der Teams, in denen die Person Spielerin ist ([BEN-02](anforderungen/08-benachrichtigungen.md)). |
| O-18 | Wann geht die Erinnerung raus? | Annahme: 12 Stunden vor der Antwortfrist ([zustaende.md](zustaende.md#termin)). |
| O-19 | Was passiert mit Serienterminen in einer neuen Ausnahme? | Annahme: ohne Antworten entfernt, mit Antworten abgesagt ([zustaende.md](zustaende.md#serie)). |
| O-34 | Bekommt eine neu erfasste Person automatisch ein Konto? | Annahme F-01: nein. Das Konto entsteht mit dem Anmeldeverfahren (O-01). Neue Personen können sich im Testmodus nicht anmelden. |
| O-35 | Dürfen alte Saisons nachträglich korrigiert werden? | Annahme F-01: nein, Saisons vor der aktuellen sind nur lesbar (SAI-02). Eine geplante Saison nach der aktuellen ist änderbar. |

## Pipeline

| Nr. | Frage | Bemerkung |
|---|---|---|
| O-30 | Vorschau-URL zum Ausprobieren | Hängt am Hosting (O-02). Bis dahin über eine Cloud-Sitzung auf dem PR-Branch. |
| O-31 | ~~Java und Migrationswerkzeug für F-00~~ | Entschieden: Java 21 und Flyway (E-57). |
| O-32 | Kontrast aktiver Navigationseintrag | `primary` auf `primary-soft` erreicht im Lightmode nur 3.97:1 (WCAG AA verlangt 4.5:1 für kleinen Text). Annahme F-00: Icon in `primary`, Text in `text`. Alternative: `primary` im Lightmode etwas dunkler. |
| O-33 | Wie lange gilt eine Sitzung? | Annahme F-00: bis zum Abmelden, ohne Ablauf. Hängt am Anmeldeverfahren (O-01). |

## swiss unihockey

| Nr. | Frage | Bemerkung |
|---|---|---|
| O-20 | Braucht die Schnittstelle einen Schlüssel oder eine Registrierung? | Die Dokumentation lässt es offen. |
| O-21 | Welche Felder liefert die Spielliste genau, und wie sind Spieltage in Turnierform abgebildet? | Gegen die echte Schnittstelle testen. |
| O-22 | Club-ID des STV Beromünster und Team-IDs | Nötig für die Verknüpfung. |
| O-23 | Erlauben die Nutzungsbedingungen den regelmässigen Abruf? | |
| O-24 | Gibt es eine neuere Schnittstelle als `api-v2`? | Siehe [06](anforderungen/06-swiss-unihockey.md#noch-zu-prüfen). |
