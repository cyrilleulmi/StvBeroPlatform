# 04 · An- und Abmeldung

Status-Legende: siehe [README](../../README.md#status-der-anforderungen).

## Antworten

| ID | Anforderung | Status |
|---|---|---|
| ANM-01 | Spielerinnen und Coaches melden sich für Termine an und ab. Coaches tun das gleich wie Spielerinnen. | Entschieden |
| ANM-02 | Pro Person und Termin gibt es genau eine Antwort, nicht eine pro Team. | Angenommen |
| ANM-03 | Mögliche Antworten: «dabei», «nicht dabei», «vielleicht». | Entschieden |
| ANM-04 | Bei «vielleicht» ist ein Kommentar Pflicht. | Entschieden |
| ANM-05 | Bei «nicht dabei» ist ein Grund freiwillig. | Angenommen |
| ANM-06 | Solange jemand nicht geantwortet hat, ist der Status «offen». Niemand ist automatisch angemeldet. | Angenommen |
| ANM-07 | Kommentare und Gründe sehen nur die Coaches der beteiligten Teams und die Person selbst. | Angenommen |
| ANM-08 | Die häufigste Aktion («dabei» / «nicht dabei») ist mit einem Tap erledigt, direkt aus der Terminliste. | Entschieden |
| ANM-09 | Eine Antwort kann jederzeit geändert werden, auch nach der Antwortfrist. | Annahme |
| ANM-10 | Ändert jemand nach der Antwortfrist von «dabei» auf «nicht dabei» oder «vielleicht», gilt das als kurzfristige Absage. Die Coaches werden benachrichtigt. | Angenommen |
| ANM-11 | Eine Coach kann stellvertretend für Spielerinnen ihres Teams antworten, z.B. wenn sich jemand mündlich abgemeldet hat. | Angenommen |
| ANM-12 | Bei stellvertretenden Antworten ist ersichtlich, wer sie eingetragen hat. | Annahme |

## Ansicht eines Termins

| ID | Anforderung | Status |
|---|---|---|
| ANM-20 | Die Ansicht zeigt die Mitglieder nach Team gruppiert. | Entschieden |
| ANM-21 | Innerhalb eines Teams sind die Spielerinnen nach Linie gruppiert: Linien in ihrer Reihenfolge, dann Goalies, dann «Ohne Linie». | Entschieden (Gruppierung), Angenommen (Reihenfolge) |
| ANM-22 | Coaches stehen in einem eigenen Block, mit Angabe ihres Teams. Sie erscheinen nicht unter den Spielerinnen. | Entschieden |
| ANM-23 | Pro Team und für den ganzen Termin wird gezählt: dabei, nicht dabei, vielleicht, offen. Gezählt werden nur Spielerinnen. | Angenommen |
| ANM-24 | Bei jeder Person ist ihr Status auf einen Blick erkennbar. | Angenommen |
| ANM-25 | Alle Mitglieder der beteiligten Teams sehen den Status der anderen, aber nicht deren Kommentare (siehe ANM-07). | Annahme |

## Doppelrollen im selben Termin

Gehört ein Termin zu mehreren Teams, kann dieselbe Person in mehr als einem davon sein.

| ID | Anforderung | Status |
|---|---|---|
| ANM-30 | Die Person gibt eine einzige Antwort. Sie gilt für den ganzen Termin. | Angenommen |
| ANM-31 | Die Person erscheint in jedem beteiligten Team in ihrer dortigen Funktion: als Spielerin in ihrer Linie, als Coach im Coach-Block. | Angenommen |
| ANM-32 | Im Team-Total zählt sie in jedem Team, in dem sie Spielerin ist. Im Gesamttotal des Termins zählt sie nur einmal. | Angenommen |

## Beispiel: gemeinsames Training von zwei Teams

```
Di 18:30 · Team 1 + Team 2          19 dabei · 4 ab · 5 offen

Team 1                              10 dabei · 2 ab · 3 offen
  Linie 1     Anna ✓  Lea ✓  Mia ✗  Sara ✓  Nina ?
  Linie 2     ...
  Goalies     Jana ✓
  Ohne Linie  Kim ✓
Team 2                              9 dabei · 2 ab · 2 offen
  Linie 1     ...

Coaches       Petra ✓ (Team 1)  Marc ✗ (Team 2)
```

Die Skizze zeigt den Aufbau, nicht das Design. Für das Aussehen gilt [design-principles.md](../design-principles.md). «Vielleicht» fehlt in der Skizze und kommt als eigener Zähler und eigenes Symbol dazu.

## Nicht Teil des Kerns

- Präsenzkontrolle (wer war wirklich da): siehe [Spätere Funktionen](../later-features.md).
- Warnungen wie «kein Goalie angemeldet»: siehe [Spätere Funktionen](../later-features.md).
