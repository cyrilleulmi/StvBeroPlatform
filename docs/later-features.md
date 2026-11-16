# Spätere Funktionen

Diese Funktionen sind bewusst zurückgestellt. Sie werden jetzt nicht umgesetzt. Das Modell soll aber so gebaut sein, dass sie später ohne Umbau dazukommen können.

## Zurückgestellt

| Funktion | Beschreibung | Was das Modell heute schon berücksichtigt | Status |
|---|---|---|---|
| **Präsenzkontrolle** | Die Coach hakt nach dem Training ab, wer wirklich da war. Daraus entsteht eine Saisonstatistik pro Spielerin. | Antwort (Absicht) und Präsenz (Tatsache) sind getrennte Dinge. Die Antwort wird nicht zur Präsenz umgedeutet. | Später (entschieden) |
| **Fahrerliste mit automatischer Fahrzeit** | Für Spieltage: wer fährt, wer fährt mit. Die Fahrzeit zum Spielort wird automatisch berechnet. | Spieltage haben Treffpunkt und Spielort mit Google-Maps-Link bzw. Koordinaten. | Später (entschieden) |
| **Elternfunktionen** | Ein Login antwortet für mehrere Kinder. | Konto und Person sind getrennt ([PTF-09](requirements/01-people-teams-roles.md)). | Später (entschieden) |
| **Gäste und Probetrainings** | Eine Spielerin aus einem anderen Team trainiert oder spielt einmalig mit. Oder jemand kommt zum Probetraining. | – | Später (angenommen) |
| **Warnungen** | Hinweise wie «kein Goalie angemeldet» oder «Linie 2 nur zu dritt». | Linien und Goalies sind eigene Gruppen, die Zähler pro Gruppe sind ableitbar. | Später (angenommen) |
| **Kalender-Abo** | Termine per iCal im eigenen Handykalender. | – | Später (angenommen) |
| **Mehrere Linien pro Spielerin** | Eine Spielerin gehört im selben Team mehreren Linien an. | Die Begrenzung auf eine Linie ist eine Regel, keine Struktur ([LIN-04](requirements/02-lines.md)). | Später (entschieden: «momentan nur eine Linie») |
| **Weitere Funktionen im Team** | Assistenzcoach, Betreuerin und Ähnliches. | Funktionen sind erweiterbar ([PTF-06](requirements/01-people-teams-roles.md)). | Offen |

## Ideen ohne Entscheid

| Idee | Beschreibung |
|---|---|
| Export der Präsenzen | Sobald es die Präsenzkontrolle gibt, liesse sich daraus eine Liste exportieren, z.B. für J+S. |

## Gestrichen

| Funktion | Entscheid |
|---|---|
| **Linien pro Termin** | Abweichende Linien für ein einzelnes Training oder einen Spieltag werden nicht benötigt. Es gibt nur Standardlinien ([LIN-08](requirements/02-lines.md)). |
