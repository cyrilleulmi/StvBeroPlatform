package ch.stvbero.platform.attendance.event;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.team.TeamDto;
import ch.stvbero.platform.core.team.TeamSeason;
import ch.stvbero.platform.core.team.TeamMembership;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Series with its exceptions (TER-05, TER-07) and all teams with their names (TER-11). */
@Schema(name = "Series")
public record SeriesDto(UUID id, SeriesType type, String title, List<UUID> teamIds, List<TeamDto> teams,
                       Weekday weekday, String startTime, String endTime, LocalDate validFrom,
                       LocalDate validUntil, LocationDto location, int responseDeadlineHours, boolean attendanceCheck,
                       List<SeriesExceptionDto> exceptions,
                       @Schema(description = "Teams invited without obligation (TER-15)") List<UUID> optionalTeamIds,
                       @Schema(description = "The events of the series exist up to this day; the app creates the next ones as time goes on, one year ahead (TER-22)") LocalDate eventsUntil) {

    public static SeriesDto of(Series s, CurrentUser user) {
        return new SeriesDto(s.id, s.type, s.title, s.teams.stream().map(t -> t.id).sorted().toList(), teams(s, user),
                s.weekday, EventRules.time(s.startTime), EventRules.time(s.endTime), s.validFrom,
                s.validUntil, s.location == null ? null : s.location.dto(), s.responseDeadlineHours, s.attendanceCheck,
                s.exceptions.stream().sorted(Comparator.comparing((SeriesException e) -> e.startDate).thenComparing(e -> e.endDate))
                        .map(e -> new SeriesExceptionDto(e.startDate, e.endDate, e.reason)).toList(),
                s.optionalTeamIds.stream().sorted().toList(), s.generatedUntil);
    }

    /**
     * All teams of the series by name, with the name in the season of the series (SAI-02) and the caller's own
     * team role.
     */
    private static List<TeamDto> teams(Series s, CurrentUser user) {
        List<UUID> ids = s.teams.stream().map(t -> t.id).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, TeamRole> mine = new HashMap<>();
        TeamMembership.<TeamMembership>list("person.id = ?1 and season.id = ?2 and team.id in ?3",
                user.personId(), s.season.id, ids).forEach(m -> mine.putIfAbsent(m.team.id, m.teamRole));
        return TeamSeason.<TeamSeason>list("season.id = ?1 and team.id in ?2", s.season.id, ids).stream()
                .map(ts -> TeamDto.of(ts, mine.get(ts.team.id)))
                .sorted(Comparator.comparing(TeamDto::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TeamDto::id))
                .toList();
    }
}
