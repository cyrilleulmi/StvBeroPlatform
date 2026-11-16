package ch.stvbero.platform.core.event;

import java.time.DayOfWeek;

/** Wochentag einer Serie (TER-05), mit deutschen Namen in der API. */
public enum Weekday {
    MONDAY(DayOfWeek.MONDAY), TUESDAY(DayOfWeek.TUESDAY), WEDNESDAY(DayOfWeek.WEDNESDAY),
    THURSDAY(DayOfWeek.THURSDAY), FRIDAY(DayOfWeek.FRIDAY), SATURDAY(DayOfWeek.SATURDAY),
    SUNDAY(DayOfWeek.SUNDAY);

    public final DayOfWeek day;

    Weekday(DayOfWeek day) {
        this.day = day;
    }
}
