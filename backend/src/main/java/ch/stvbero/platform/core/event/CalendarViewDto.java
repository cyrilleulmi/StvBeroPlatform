package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.absence.AbsenceDto;
import ch.stvbero.platform.core.team.TeamRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Calendar view of a period (KAL): the teams to choose from, which of them are shown, the events of these teams
 * and the own absences in the period.
 */
@Schema(name = "CalendarView")
public record CalendarViewDto(LocalDate from, LocalDate to,
                              @Schema(description = "Teams to choose from: own teams, for coaches and admins all teams of the current season")
                              List<Team> teams,
                              @Schema(description = "Teams shown without a choice: coaches their coached teams, everyone else all own teams (KAL-03, KAL-04)")
                              List<UUID> defaultTeamIds,
                              @Schema(description = "Teams shown now")
                              List<UUID> teamIds,
                              @Schema(description = "All teams are shown (KAL-04)")
                              boolean allTeams,
                              @Schema(description = "The caller may show all teams: coaches and admins (KAL-04)")
                              boolean canShowAllTeams,
                              List<EventSummaryDto> events,
                              @Schema(description = "Own absences in the period (ABS-07)")
                              List<AbsenceDto> absences) {

    /** A team to choose from, with the own team role if any. */
    @Schema(name = "CalendarTeam")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Team(UUID id, String name, TeamRole myTeamRole) {
    }
}
