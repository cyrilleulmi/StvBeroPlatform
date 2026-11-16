package ch.stvbero.platform.kern.benachrichtigung;

/** Auslöser einer Benachrichtigung, entspricht BEN-01 bis BEN-07. */
public enum BenachrichtigungArt {
    /** BEN-01: Antwortfrist rückt näher, noch keine Antwort. */
    ERINNERUNG,
    /** BEN-02: Wechsel weg von «dabei» nach der Antwortfrist. */
    KURZFRISTIGE_ABSAGE,
    /** BEN-03: Aufgebot veröffentlicht. */
    AUFGEBOT_VEROEFFENTLICHT,
    /** BEN-05: veröffentlichtes Aufgebot geändert. */
    AUFGEBOT_GEAENDERT,
    /** BEN-04: aufgebotene Spielerin meldet sich ab. */
    AUFGEBOTENE_ABGEMELDET,
    /** BEN-06: importiertes Spiel geändert (kommt mit F-07). */
    SPIEL_GEAENDERT,
    /** BEN-07: Termin abgesagt. */
    TERMIN_ABGESAGT,
    /** BEN-07: Termin verschoben. */
    TERMIN_VERSCHOBEN
}
