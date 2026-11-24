package ch.stvbero.platform.attendance.calendar;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * What the person's participation in an event looks like for the filter of the calendar feed (CAL-11, CAL-12).
 * {@code ATTENDING} is always delivered and therefore not part of a filter.
 * <ul>
 *   <li>{@code PENDING}: no response yet, as a member of an expected team</li>
 *   <li>{@code OPTIONAL_PENDING}: no response yet, invited without obligation («freiwillig», TER-15)</li>
 *   <li>{@code MAYBE}: «vielleicht»</li>
 *   <li>{@code DECLINED}: «nicht dabei», or not called up in a published call-up (CAL-12)</li>
 * </ul>
 */
@Schema(name = "CalendarResponse")
public enum CalendarResponse {
    PENDING, OPTIONAL_PENDING, MAYBE, DECLINED
}
