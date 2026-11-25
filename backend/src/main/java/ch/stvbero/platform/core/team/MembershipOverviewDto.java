package ch.stvbero.platform.core.team;

import ch.stvbero.platform.core.person.PersonDto;
import ch.stvbero.platform.core.season.SeasonDto;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Who is in which team, for the whole club in one season (PTF-16): the page «Teamzuteilung» and the step «Personen»
 * of the assistant «Neues Team» show it as a table of people and teams.
 */
@Schema(name = "MembershipOverview")
public record MembershipOverviewDto(
        SeasonDto season,
        @Schema(nullable = true, description = "The season before, to see where someone was and move people on; missing for the first season") SeasonDto previousSeason,
        @Schema(description = "Teams of the season by name, then the teams of the season before that are not in this season yet") List<TeamColumn> teams,
        @Schema(description = "Teams of the season before with their names of that time") List<TeamColumn> previousTeams,
        @Schema(description = "Every person of the club, by last name") List<Row> people,
        @Schema(description = "The caller may take people out of a team (admins and coaches, E-103)") boolean canRemove,
        @Schema(description = "The season can be changed (not archived, SAI-02)") boolean writable) {

    @Schema(name = "MembershipTeam")
    public record TeamColumn(UUID id, String name,
                             @Schema(description = "The team is part of the season; otherwise it joins with its first member") boolean inSeason) {
    }

    @Schema(name = "MembershipRow")
    public record Row(PersonDto person,
                      @Schema(description = "Teams of the person in the season") List<Entry> teams,
                      @Schema(description = "Teams of the person in the season before") List<Entry> previousTeams) {
    }

    @Schema(name = "MembershipEntry")
    public record Entry(UUID teamId, TeamRole teamRole) {
    }

    @Schema(name = "MembershipBatchResult")
    public record BatchResult(@Schema(description = "People newly in a team") int added,
                              @Schema(description = "Team roles changed") int changed,
                              @Schema(description = "People removed from a team") int removed) {
    }
}
