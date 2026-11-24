package ch.stvbero.platform.core.calendar;

import ch.stvbero.platform.core.event.EventType;
import ch.stvbero.platform.core.event.Participation;
import ch.stvbero.platform.core.event.ResponseStatus;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Which events the calendar feed of a person delivers (CAL-10 to CAL-15). Without database, so that the rules
 * can be tested directly.
 *
 * @param types           event types in the feed (CAL-10)
 * @param responses       participations in the feed besides «dabei», which is always in it (CAL-11)
 * @param excludedTeamIds teams whose events the person does not want (CAL-13)
 */
public record CalendarFilter(Set<EventType> types, Set<CalendarResponse> responses, Set<UUID> excludedTeamIds) {

    /** Everything, as before the filters (F-18). Default of every address (CAL-14). */
    public static CalendarFilter all() {
        return new CalendarFilter(EnumSet.allOf(EventType.class), EnumSet.allOf(CalendarResponse.class), Set.of());
    }

    /**
     * Participation of the person for the filter (CAL-11, CAL-12). A published call-up decides for the players of
     * the match day: called up counts as «dabei» unless she cancelled, not called up counts like «nicht dabei».
     * Coaches are never part of a call-up (AUF-28), their response decides.
     *
     * @param response      own response, {@code null} or {@code PENDING} without one
     * @param participation expected or invited without obligation (TER-15)
     * @param calledUp      {@code null} if there is no published call-up that concerns the person as a player
     * @return {@code null} for «dabei»
     */
    public static CalendarResponse participation(ResponseStatus response, Participation participation,
                                                 Boolean calledUp) {
        ResponseStatus status = response == null ? ResponseStatus.PENDING : response;
        if (calledUp != null) {
            if (!calledUp || status == ResponseStatus.DECLINED) {
                return CalendarResponse.DECLINED;
            }
            return null;
        }
        return switch (status) {
            case ATTENDING -> null;
            case DECLINED -> CalendarResponse.DECLINED;
            case MAYBE -> CalendarResponse.MAYBE;
            case PENDING -> participation == Participation.OPTIONAL
                    ? CalendarResponse.OPTIONAL_PENDING : CalendarResponse.PENDING;
        };
    }

    /**
     * Whether an event is in the feed: its type is chosen, at least one of the person's teams in it is not
     * excluded, and the participation is «dabei» or chosen. Cancelled events follow the same rules, so that an
     * entry the calendar already shows is struck through instead of disappearing (CAL-15).
     *
     * @param myTeams the person's teams among the teams of the event
     */
    public boolean includes(EventType type, Collection<UUID> myTeams, CalendarResponse participation) {
        if (!types.contains(type)) {
            return false;
        }
        if (!myTeams.isEmpty() && excludedTeamIds.containsAll(myTeams)) {
            return false;
        }
        return participation == null || responses.contains(participation);
    }
}
