package ch.stvbero.platform.core.notification;

/** Auslöser einer Benachrichtigung, entspricht BEN-01 bis BEN-07. */
public enum NotificationType {
    /** BEN-01: Antwortfrist rückt näher, noch keine Antwort. */
    REMINDER,
    /** BEN-02: Wechsel weg von «dabei» nach der Antwortfrist. */
    LATE_CANCELLATION,
    /** BEN-03: Aufgebot veröffentlicht. */
    CALL_UP_PUBLISHED,
    /** BEN-05: veröffentlichtes Aufgebot geändert. */
    CALL_UP_CHANGED,
    /** BEN-04: aufgebotene Spielerin meldet sich ab. */
    CALLED_UP_PLAYER_DECLINED,
    /** BEN-06: importiertes Spiel geändert (kommt mit F-07). */
    MATCH_CHANGED,
    /** BEN-07: Termin abgesagt. */
    EVENT_CANCELLED,
    /** BEN-07: Termin verschoben. */
    EVENT_RESCHEDULED
}
