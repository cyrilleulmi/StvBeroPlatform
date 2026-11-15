# Zustände und Abläufe

Zustandsdiagramme der fachlichen Objekte und Sequenzdiagramme der wichtigsten Abläufe. Der Test-Agent leitet daraus Testfälle ab: Jeder Übergang ist ein Testfall, jeder nicht erlaubte Übergang auch.

Wer das Verhalten ändert, passt das Diagramm im selben PR an. Diagramme sind [Mermaid](https://mermaid.js.org) und werden von GitHub direkt dargestellt.

## Antwort einer Person auf einen Termin

```mermaid
stateDiagram-v2
    direction LR
    [*] --> OFFEN: Termin entsteht / Person kommt ins Team
    OFFEN --> DABEI: «dabei»
    OFFEN --> NICHT_DABEI: «nicht dabei» (Grund freiwillig)
    OFFEN --> VIELLEICHT: «vielleicht» + Kommentar
    DABEI --> NICHT_DABEI
    DABEI --> VIELLEICHT: + Kommentar
    NICHT_DABEI --> DABEI
    NICHT_DABEI --> VIELLEICHT: + Kommentar
    VIELLEICHT --> DABEI
    VIELLEICHT --> NICHT_DABEI
    DABEI --> [*]: Termin vorbei oder abgesagt
    NICHT_DABEI --> [*]: Termin vorbei oder abgesagt
    VIELLEICHT --> [*]: Termin vorbei oder abgesagt
    OFFEN --> [*]: Termin vorbei oder abgesagt

    note right of VIELLEICHT
        Ohne Kommentar: 422, Regel ANM-04.
        Zurück nach OFFEN gibt es nicht.
    end note
```

| Regel | Bezug |
|---|---|
| Jeder Wechsel ist erlaubt, auch nach der Antwortfrist. | ANM-09 |
| Wechsel **weg von DABEI** nach der Antwortfrist setzt `kurzfristig` und benachrichtigt die Coaches. | ANM-10, BEN-02 |
| Bei einer aufgebotenen Spielerin benachrichtigt jeder Wechsel weg von DABEI die Coaches, auch vor der Frist. | AUF-26, BEN-04 |
| Stellvertretend gesetzte Antworten folgen denselben Übergängen und speichern `eingetragenVon`. | ANM-11, ANM-12 |
| Nach Absage oder Ende des Termins sind keine Änderungen mehr möglich (409). | TER-06 |

## Termin

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Geplant: erfasst oder von Serie erzeugt
    state Geplant {
        direction LR
        [*] --> AntwortenOffen
        AntwortenOffen --> NachFrist: Antwortfrist erreicht
        NachFrist --> Laeuft: Beginn
    }
    Geplant --> Geplant: verschoben (Beginn geändert)<br/>Benachrichtigung BEN-07
    Geplant --> Abgesagt: absagen + Grund<br/>Benachrichtigung BEN-07
    Geplant --> Vorbei: Ende erreicht
    Abgesagt --> [*]
    Vorbei --> [*]
```

- Gespeichert wird nur `GEPLANT` oder `ABGESAGT`. «Nach Frist», «läuft» und «vorbei» ergeben sich aus der Backend-Uhr.
- Eine Erinnerung (BEN-01) geht 12 Stunden vor der Antwortfrist an alle mit Antwort OFFEN, höchstens einmal pro Person und Termin (BEN-13). Die 12 Stunden sind eine Annahme.
- Verschieben behält alle Antworten. Ob «dabei» nach einer Verschiebung noch gilt, entscheidet die Person selbst.

## Serie

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Aktiv: angelegt, erzeugt Termine im Gültigkeitszeitraum
    Aktiv --> Aktiv: geändert, betrifft künftige Termine (TER-08)
    Aktiv --> Aktiv: Ausnahme hinzugefügt (TER-07)
    Aktiv --> Beendet: Gültigkeit abgelaufen
    Beendet --> [*]
```

Beim Hinzufügen einer Ausnahme (z.B. Ferien) gilt für die betroffenen künftigen Termine:

- Termin ohne Antworten: wird entfernt.
- Termin mit Antworten: wird abgesagt, Grund = Grund der Ausnahme. So geht keine Antwort verloren und alle werden benachrichtigt.

Das ist eine Annahme.

## Aufgebot eines Spieltags

```mermaid
stateDiagram-v2
    direction LR
    [*] --> KEINES
    KEINES --> ENTWURF: Coach wählt erste Spielerin
    ENTWURF --> ENTWURF: Auswahl ändern
    ENTWURF --> VEROEFFENTLICHT: veröffentlichen<br/>alle Spielerinnen benachrichtigt (BEN-03)
    VEROEFFENTLICHT --> VEROEFFENTLICHT: Auswahl ändern<br/>nur Betroffene benachrichtigt (BEN-05)
    VEROEFFENTLICHT --> [*]: Spieltag vorbei
```

Sicht einer Spielerin (`meinAufgebot`):

```mermaid
stateDiagram-v2
    direction LR
    [*] --> NOCH_KEIN_AUFGEBOT
    NOCH_KEIN_AUFGEBOT --> AUFGEBOTEN: veröffentlicht, ausgewählt
    NOCH_KEIN_AUFGEBOT --> NICHT_AUFGEBOTEN: veröffentlicht, nicht ausgewählt
    AUFGEBOTEN --> NICHT_AUFGEBOTEN: Coach ändert
    NICHT_AUFGEBOTEN --> AUFGEBOTEN: Coach ändert
```

- Solange das Aufgebot ein Entwurf ist, sehen es nur Coaches (AUF-23).
- Aufgeboten werden kann jede Spielerin des Teams, unabhängig von ihrer Antwort (AUF-22).
- Aufgebotene bestätigen nicht (AUF-25).

## Spiel aus swiss unihockey

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Aktuell: Abgleich findet neues Spiel<br/>Spieltag wird angelegt oder ergänzt
    Aktuell --> Aktuell: Abgleich ohne Änderung
    Aktuell --> Geaendert: Zeit, Ort oder Gegner bei SU geändert
    Geaendert --> Aktuell: übernommen, Team benachrichtigt (BEN-06)
    Aktuell --> NichtMehrVorhanden: SU liefert Spiel nicht mehr
    NichtMehrVorhanden --> Aktuell: SU liefert es wieder
    NichtMehrVorhanden --> [*]: Coach löscht es
```

- Der Abgleich überschreibt nur importierte Felder: Gegner, Anspielzeit, Heim/Auswärts, Spielort, Resultat. Treffpunkt, Hinweise, Antworten und Aufgebot bleiben unberührt (SUH-06).
- Spiele desselben Teams am selben Tag in derselben Halle bilden einen Spieltag (SUH-04).
- Ist swiss unihockey nicht erreichbar, ändert sich nichts. Der nächste Lauf versucht es wieder (SUH-09).

## Benachrichtigung

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Ungelesen: Auslöser BEN-01 bis BEN-07
    Ungelesen --> Gelesen: geöffnet oder als gelesen markiert
    Gelesen --> [*]
```

## Abläufe

### Antworten mit einem Tap

```mermaid
sequenceDiagram
    actor S as Spielerin
    participant F as Frontend
    participant B as Backend
    participant N as Benachrichtigungen
    S->>F: tippt «nicht dabei» in der Terminliste
    F->>B: PUT /api/termine/{id}/antworten/ich {NICHT_DABEI}
    B->>B: Termin geplant? Person Mitglied eines beteiligten Teams?
    B->>B: Wechsel weg von DABEI nach Frist? → kurzfristig
    alt kurzfristig oder aufgeboten
        B->>N: Coaches der Teams der Person benachrichtigen
    end
    B-->>F: 200 Antwort
    F-->>S: Button aktiv, Zähler aktualisiert, Hinweis «Gespeichert» (role=status)
```

### Aufgebot veröffentlichen

```mermaid
sequenceDiagram
    actor C as Coach
    participant F as Frontend
    participant B as Backend
    participant N as Benachrichtigungen
    C->>F: öffnet /termine/{id}/aufgebot
    F->>B: GET /api/termine/{id}/aufgebot
    B-->>F: alle Spielerinnen nach Linie, mit Antwort
    C->>F: wählt aus
    F->>B: PUT /api/termine/{id}/aufgebot {personIds}
    B-->>F: Status ENTWURF
    C->>F: «Veröffentlichen» und bestätigen
    F->>B: POST /api/termine/{id}/aufgebot/veroeffentlichen
    B->>N: alle Spielerinnen des Teams benachrichtigen
    B-->>F: Status VEROEFFENTLICHT
```

### Abgleich mit swiss unihockey

```mermaid
sequenceDiagram
    participant J as Täglicher Job
    participant B as Backend
    participant SU as swiss unihockey (Simulator im Test)
    participant N as Benachrichtigungen
    J->>B: Abgleich für alle verknüpften Teams
    B->>SU: Spiele des Teams in der Saison
    alt nicht erreichbar
        SU--xB: Fehler
        B->>B: nichts ändern, protokollieren
    else Antwort
        SU-->>B: Spiele
        B->>B: nach Tag + Halle zu Spieltagen gruppieren
        B->>B: neue anlegen, geänderte übernehmen, fehlende markieren
        B->>N: Team über geänderte Spiele benachrichtigen
    end
```
