package ch.stvbero.platform.core.statistics;

import ch.stvbero.platform.core.event.AttendanceStatus;
import ch.stvbero.platform.core.event.EventType;
import ch.stvbero.platform.core.event.OverviewDto;
import ch.stvbero.platform.core.event.ResponseStatus;
import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Response types of the attendance statistics (STA). */
public final class StatisticsDto {

    private StatisticsDto() {
    }

    /** Season the statistics are based on; the period lies within it. */
    @Schema(name = "StatisticsSeason")
    public record SeasonRef(UUID id, String name, LocalDate startDate, LocalDate endDate) {
    }

    /** Numbers of one player in one team (STA-03, STA-04). Rates in percent, {@code null} without a base. */
    @Schema(name = "PlayerStatistics")
    public record Player(PersonSummary person, LocalDate joinedOn, int events, int attending, int declined, int maybe,
                         int pending, Integer attendingRate, int lateCancellations, int attendanceEvents,
                         int present, int absent, int excused, Integer attendanceRate) {

        static Player of(PersonSummary person, LocalDate joinedOn, StatisticsRules.Numbers n) {
            return new Player(person, joinedOn, n.events(), n.attending(), n.declined(), n.maybe(), n.pending(),
                    n.attendingRate(), n.lateCancellations(), n.attendanceEvents(), n.present(), n.absent(),
                    n.excused(), n.attendanceRate());
        }
    }

    /** One line, the goalies or «Ohne Linie»; players by name. */
    @Schema(name = "StatisticsGroup")
    public record Group(UUID groupId, OverviewDto.Type type, String name, List<Player> players) {
    }

    /** Team average (STA-05): mean per player. */
    @Schema(name = "StatisticsAverage")
    public record Average(double events, double attending, double declined, double maybe, double pending,
                          Integer attendingRate, double lateCancellations, double present, double absent,
                          double excused, Integer attendanceRate) {

        static Average of(StatisticsRules.Average a) {
            return a == null ? null : new Average(a.events(), a.attending(), a.declined(), a.maybe(), a.pending(),
                    a.attendingRate(), a.lateCancellations(), a.present(), a.absent(), a.excused(),
                    a.attendanceRate());
        }
    }

    /**
     * Statistics of a team (STA-01 to STA-05). {@code events}: events that count in the period;
     * {@code attendanceEvents}: those of them with recorded attendance, the base of the attendance rate.
     */
    @Schema(name = "TeamStatistics")
    public record TeamStatistics(OverviewDto.TeamName team, List<OverviewDto.TeamName> teams, SeasonRef season,
                                 LocalDate from, LocalDate to, EventType type, int events, int attendanceEvents,
                                 List<Group> groups, Average average) {
    }

    /** One event in the list of a player (STA-06). {@code attendance}: only where recorded. */
    @Schema(name = "StatisticsEvent")
    public record EventEntry(UUID id, EventType type, String title, OffsetDateTime start, ResponseStatus response,
                             boolean lateCancellation, AttendanceStatus attendance) {
    }

    /**
     * Statistics of one player (STA-06, STA-08): her numbers in the team and the events in the period, newest
     * first. {@code teams}: the teams she plays in this season. Without such a team {@code team} and
     * {@code statistics} are {@code null}.
     */
    @Schema(name = "PersonStatistics")
    public record PersonStatistics(PersonSummary person, OverviewDto.TeamName team, List<OverviewDto.TeamName> teams,
                                   SeasonRef season, LocalDate from, LocalDate to, EventType type,
                                   Player statistics, List<EventEntry> events) {
    }
}
