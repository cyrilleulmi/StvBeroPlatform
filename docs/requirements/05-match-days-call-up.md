# 05 · Spieltage und Aufgebot

Das Aufgebot für Spiele ist Teil des Kerns.

Status-Legende: siehe [README](../../README.md#status-der-anforderungen).

## Turnierform

Die Teams spielen in Turnierform: An einem Spieltag finden mehrere Spiele in derselben Halle statt. Deshalb ist die Einheit der App der **Spieltag**, nicht das einzelne Spiel.

| ID | Anforderung | Status |
|---|---|---|
| AUF-01 | Ein Spieltag ist ein Termin, der mehrere Spiele in derselben Halle umfasst. | Entschieden |
| AUF-02 | Pro Spieltag gibt es eine Anmeldung und ein Aufgebot, nicht pro Spiel. | Entschieden |
| AUF-03 | Ein Spieltag gehört zu genau einem Team. | Annahme |
| AUF-04 | Ein einzelnes Spiel ausserhalb der Turnierform (z.B. Testspiel, Cup) ist ein Spieltag mit einem Spiel. | Annahme |

## Angaben zum Spieltag

| ID | Anforderung | Status |
|---|---|---|
| AUF-10 | Der Spielort kann mit einem Google-Maps-Link angegeben werden. | Entschieden |
| AUF-11 | Der Google-Maps-Link lässt sich von Hand einfügen. Bei importierten Spieltagen wird er automatisch gesetzt (siehe [06](06-swiss-unihockey.md)). | Angenommen |
| AUF-12 | Ein Spieltag hat einen Treffpunkt mit Zeit und Ort. | Angenommen |
| AUF-13 | Ein Spieltag hat ein freies Feld für Hinweise, z.B. die Trikotfarbe. | Angenommen |
| AUF-14 | Jedes Spiel des Spieltags hat Gegner, Anspielzeit und die Angabe Heim oder Auswärts. | Angenommen |
| AUF-15 | Zu jedem Spiel kann ein Resultat festgehalten werden. | Annahme |
| AUF-16 | Spieltage lassen sich von Hand erfassen, unabhängig von der Anbindung an swiss unihockey. | Angenommen |

## Ablauf

1. Die Spielerinnen melden sich für den Spieltag an oder ab, genau wie für ein Training ([04](04-responses.md)).
2. Die Coach wählt das Aufgebot.
3. Die Coach veröffentlicht das Aufgebot. Alle werden benachrichtigt.

| ID | Anforderung | Status |
|---|---|---|
| AUF-20 | Die An- und Abmeldung für einen Spieltag funktioniert gleich wie für ein Training. | Angenommen |
| AUF-21 | Die Coach wählt die Spielerinnen für das Aufgebot aus. Die Auswahlliste ist nach den Standardlinien gruppiert (Linien, Goalies, Ohne Linie). Lücken durch Absagen sind sofort sichtbar. | Angenommen |
| AUF-22 | Die Coach darf alle Spielerinnen des Teams aufbieten, unabhängig von deren Antwort. Der Antwortstatus steht daneben. So kann sie auch jemanden mit «vielleicht» oder ohne Antwort nominieren. | Entschieden |
| AUF-23 | Das Aufgebot wird ausdrücklich veröffentlicht. Vorher sehen es nur die Coaches. | Annahme |
| AUF-24 | Mit der Veröffentlichung werden alle Spielerinnen des Teams benachrichtigt. Jede sieht bei sich «aufgeboten» oder «nicht aufgeboten». | Angenommen |
| AUF-25 | Aufgebotene müssen nicht bestätigen. Die Zusage liegt mit der Anmeldung schon vor. | Entschieden |
| AUF-26 | Wer aufgeboten ist und doch nicht kann, meldet sich ab. Die Coach wird benachrichtigt. | Entschieden |
| AUF-27 | Ändert die Coach ein veröffentlichtes Aufgebot, werden die betroffenen Spielerinnen benachrichtigt. | Annahme |
| AUF-28 | Coaches melden sich für den Spieltag an und ab, stehen im Coach-Block und sind nicht Teil des Aufgebots. | Angenommen |

## Nicht Teil des Kerns

- Abweichende Linien für einen einzelnen Spieltag: gestrichen (siehe [LIN-08](02-lines.md)).
- Fahrerliste mit automatischer Fahrzeit: siehe [Spätere Funktionen](../later-features.md). Treffpunkt und Spielort sind bewusst schon im Modell, damit das später darauf aufsetzen kann.
- Gastspielerinnen aus anderen Teams aufbieten: siehe [Spätere Funktionen](../later-features.md).
