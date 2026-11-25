package ch.stvbero.platform.attendance.event;

import java.time.DayOfWeek;

/** Weekday of a series (TER-05). */
public enum Weekday {
    MONDAY(DayOfWeek.MONDAY), TUESDAY(DayOfWeek.TUESDAY), WEDNESDAY(DayOfWeek.WEDNESDAY),
    THURSDAY(DayOfWeek.THURSDAY), FRIDAY(DayOfWeek.FRIDAY), SATURDAY(DayOfWeek.SATURDAY),
    SUNDAY(DayOfWeek.SUNDAY);

    public final DayOfWeek day;

    Weekday(DayOfWeek day) {
        this.day = day;
    }
}
