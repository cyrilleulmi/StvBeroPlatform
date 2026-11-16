package ch.stvbero.platform.kern.termin;

import java.time.DayOfWeek;

/** Wochentag einer Serie (TER-05), mit deutschen Namen in der API. */
public enum Wochentag {
    MONTAG(DayOfWeek.MONDAY), DIENSTAG(DayOfWeek.TUESDAY), MITTWOCH(DayOfWeek.WEDNESDAY),
    DONNERSTAG(DayOfWeek.THURSDAY), FREITAG(DayOfWeek.FRIDAY), SAMSTAG(DayOfWeek.SATURDAY),
    SONNTAG(DayOfWeek.SUNDAY);

    public final DayOfWeek tag;

    Wochentag(DayOfWeek tag) {
        this.tag = tag;
    }
}
