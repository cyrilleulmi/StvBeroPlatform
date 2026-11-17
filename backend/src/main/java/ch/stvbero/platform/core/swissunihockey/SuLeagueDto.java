package ch.stvbero.platform.core.swissunihockey;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * League view as the simulator delivers it (test support, F-17): league, group, table and the matches of the
 * league view. Without {@code matches} the league view shows the matches of the sync.
 */
@Schema(name = "SuLeague", description = "League view the simulator delivers (F-17)")
public record SuLeagueDto(String league,
                          String group,
                          @NotNull List<@Valid @NotNull Standing> table,
                          List<@Valid @NotNull SuMatchDto> matches) {

    /** A table row. {@code suTeamId} marks the own team if it equals the linked team ID. */
    @Schema(name = "SuStanding")
    public record Standing(@NotNull @Positive Integer rank,
                           @Positive Integer suTeamId,
                           @NotBlank String team,
                           @NotNull @PositiveOrZero Integer matches,
                           @NotNull @PositiveOrZero Integer wins,
                           @NotNull @PositiveOrZero Integer draws,
                           @NotNull @PositiveOrZero Integer losses,
                           @NotNull @PositiveOrZero Integer goalsFor,
                           @NotNull @PositiveOrZero Integer goalsAgainst,
                           @NotNull Integer points) {

        SuStanding toStanding() {
            return new SuStanding(rank, suTeamId, team.strip(), matches, wins, draws, losses, goalsFor, goalsAgainst,
                    points);
        }
    }

    /** @param syncMatches the matches of the sync, used if the league view has none of its own */
    public SuLeague toLeague(List<SuMatch> syncMatches) {
        return new SuLeague(empty(league), empty(group), table.stream().map(Standing::toStanding).toList(),
                matches == null ? syncMatches : matches.stream().map(SuMatchDto::toMatch).toList());
    }

    private static String empty(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
