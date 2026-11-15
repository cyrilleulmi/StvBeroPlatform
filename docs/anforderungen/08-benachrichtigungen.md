# 08 · Benachrichtigungen

Status-Legende: siehe [README](../../README.md#status-der-anforderungen).

## Anforderungen

| ID | Auslöser | Wer wird benachrichtigt | Status |
|---|---|---|---|
| BEN-01 | Die Antwortfrist eines Termins rückt näher und jemand hat noch nicht geantwortet. | Die Person ohne Antwort | Angenommen |
| BEN-02 | Kurzfristige Absage: Jemand wechselt nach der Antwortfrist auf «nicht dabei» oder «vielleicht». | Coaches der beteiligten Teams | Angenommen |
| BEN-03 | Ein Aufgebot wird veröffentlicht. | Alle Spielerinnen des Teams | Angenommen |
| BEN-04 | Eine aufgebotene Spielerin meldet sich ab. | Coaches des Teams | Entschieden |
| BEN-05 | Ein veröffentlichtes Aufgebot wird geändert. | Die betroffenen Spielerinnen | Annahme |
| BEN-06 | Ein importiertes Spiel wird verschoben oder geändert. | Alle Mitglieder des Teams | Angenommen |
| BEN-07 | Ein Termin wird abgesagt oder verschoben. | Alle Mitglieder der beteiligten Teams | Annahme |

## Weitere Anforderungen

| ID | Anforderung | Status |
|---|---|---|
| BEN-10 | Über welchen Kanal benachrichtigt wird (E-Mail, Push über die PWA oder beides), ist nicht festgelegt. | Offen |
| BEN-11 | Die Benachrichtigungen sind so gebaut, dass der Kanal austauschbar ist und weitere Kanäle dazukommen können. | Annahme |
| BEN-12 | Aus einer Benachrichtigung gelangt man direkt zum betroffenen Termin. | Annahme |
| BEN-13 | Pro Auslöser gibt es höchstens eine Erinnerung. Niemand wird mehrfach an denselben Termin erinnert. | Annahme |
