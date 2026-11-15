# 02 · Linien

Linien sind einer der beiden Hauptgründe für die eigene App: Spielerinnen sollen bei An- und Abmeldungen direkt nach Linie gruppiert erscheinen.

Status-Legende: siehe [README](../../README.md#status-der-anforderungen).

## Anforderungen

| ID | Anforderung | Status |
|---|---|---|
| LIN-01 | Spielerinnen können Linien zugewiesen werden. | Entschieden |
| LIN-02 | Eine Spielerin gehört momentan höchstens einer Linie an. | Entschieden |
| LIN-03 | Linien gehören zu einem Team. Die Regel aus LIN-02 gilt pro Team: Eine Spielerin in zwei Teams kann in jedem Team eine eigene Linie haben. | Angenommen |
| LIN-04 | Die Begrenzung auf eine Linie ist als Regel umgesetzt, nicht als feste Struktur. «Mehrere Linien pro Spielerin» soll später ohne Umbau des Modells möglich sein. | Angenommen |
| LIN-05 | Goalies bilden pro Team eine eigene Gruppe neben den Linien. Eine Spielerin ist entweder in einer Linie oder bei den Goalies. | Angenommen |
| LIN-06 | Spielerinnen ohne Zuweisung erscheinen in der Anzeige unter «Ohne Linie». | Angenommen |
| LIN-07 | Die Coach eines Teams verwaltet dessen Linien: Linien anlegen, umbenennen, löschen und Spielerinnen per Drag & Drop zuweisen. Auf dem Handy muss das auch ohne Drag & Drop gehen (Touch). | Angenommen |
| LIN-08 | Es gibt nur Standardlinien. Abweichende Linien für einen einzelnen Termin werden nicht benötigt. | Entschieden |
| LIN-09 | Linien haben einen Namen und eine Reihenfolge (Linie 1, Linie 2, …). Die Reihenfolge bestimmt die Anzeige. | Annahme |
| LIN-10 | Nur Personen mit der Funktion «Spielerin» im jeweiligen Team können einer Linie oder den Goalies zugewiesen werden. | Annahme |
| LIN-11 | Linien gelten pro Saison (siehe [09 · Saisons](09-saisons.md)). | Angenommen |
| LIN-12 | Bei An- und Abmeldungen sowie im Aufgebot werden Spielerinnen nach Linie gruppiert angezeigt (siehe [04](04-an-und-abmeldung.md) und [05](05-spieltage-aufgebot.md)). | Entschieden |

## Anzeige-Reihenfolge innerhalb eines Teams

1. Linien in ihrer Reihenfolge
2. Goalies
3. Ohne Linie
