package ch.stvbero.platform.core.calendar;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Personal address of the calendar feed (CAL-01) with its filter (CAL-10 to CAL-14).
 *
 * @param feedPath path of the feed with the token, e.g. {@code /api/calendar/<token>.ics}. The app puts its own
 *                 address in front of it.
 * @param filter   which events the feed delivers
 */
@Schema(name = "CalendarSubscription")
public record CalendarSubscriptionDto(
        @Schema(required = true, examples = "/api/calendar/q3XyR0p8vUu6Yc1yZf9xwGx0N2m7oVbE4hT5sK1dLaA.ics")
        String feedPath,
        @Schema(required = true) CalendarFilterDto filter) {
}
