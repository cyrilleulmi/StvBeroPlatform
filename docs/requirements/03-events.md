# 03 · Termine

Dass ein Training zu mehreren Teams gleichzeitig gehören kann, ist einer der beiden Hauptgründe für die eigene App.

Status-Legende: siehe [README](../../README.md#status-der-anforderungen).

## Anforderungen

| ID | Anforderung | Status |
|---|---|---|
| TER-01 | Ein Training kann zu mehreren Teams gleichzeitig gehören. | Entschieden |
| TER-02 | Es gibt von Anfang an die Termintypen Training, Spieltag und Anlass. Die An- und Abmeldung funktioniert bei allen gleich. | Angenommen |
| TER-03 | Jeder Termin gehört zu mindestens einem Team. | Angenommen |
| TER-04 | Ein Termin hat Datum, Beginn, Ende, Ort und ein freies Feld für Hinweise. | Annahme |
| TER-05 | Serientermine: Ein wiederkehrender Termin wird einmal erfasst, z.B. «jeden Dienstag 18:30, Halle X, Team 1 + Team 2». | Angenommen |
| TER-06 | Einzelne Daten einer Serie lassen sich absagen oder verschieben, ohne die Serie zu ändern. | Angenommen |
| TER-07 | Ferien und andere Zeiträume lassen sich von einer Serie ausnehmen. | Angenommen |
| TER-08 | Änderungen an einer Serie gelten für künftige Termine. Bereits gegebene Antworten bleiben erhalten. | Annahme |
| TER-09 | Termine haben eine Antwortfrist: X Stunden vor Beginn. Sie steuert Erinnerungen und was als kurzfristig gilt (siehe [04](04-responses.md) und [08](08-notifications.md)). | Angenommen |
| TER-10 | Die Antwortfrist ist pro Termin bzw. Serie einstellbar, mit einem sinnvollen Standardwert. | Annahme |
| TER-11 | Die Coach eines beteiligten Teams kann Termine erfassen, bearbeiten und absagen. | Angenommen |
| TER-12 | Wird ein Termin abgesagt oder verschoben, werden alle Betroffenen benachrichtigt. | Annahme |
| TER-13 | Jede Person sieht die kommenden Termine aller ihrer Teams in einer Liste, mit ihrer eigenen Antwort. | Annahme |

## Termintypen

| Typ | Teams | Besonderheiten |
|---|---|---|
| Training | eines oder mehrere | Meist als Serie. |
| Spieltag | eines (Annahme) | Umfasst mehrere Spiele (Turnierform), hat Treffpunkt, Spielort mit Google-Maps-Link und ein Aufgebot. Details: [05 · Spieltage und Aufgebot](05-match-days-call-up.md). |
| Anlass | eines oder mehrere | Sonstiges, z.B. Teamanlass. Nur An- und Abmeldung. |

## Nicht Teil des Kerns

- Kalender-Abo (iCal): siehe [Spätere Funktionen](../later-features.md).
