package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.person.PersonSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Driver plan of a team for a whole season (DRV-14): every match day with its cars, and how the trips are shared
 * among the members (DRV-17). {@code members} (the people who can be entered) only for coaches and admins.
 */
@Schema(name = "DriverPlan")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DriverPlanDto(
        UUID teamId, String teamName, UUID seasonId, String seasonName,
        @Schema(description = "The caller is a member of the team and can enter herself as driver") boolean canJoin,
        @Schema(description = "Coach or admin: enters drivers for others") boolean canManage,
        @Schema(description = "Match days of the team in the season, by start") List<MatchDay> matchDays,
        @Schema(description = "Trips per member of the team, by name (DRV-17)") List<Share> shares,
        @Schema(description = "Members of the team, by name; only for coaches and admins") List<PersonSummary> members) {

    /** A match day in the plan. */
    @Schema(name = "DriverPlanMatchDay")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MatchDay(
            UUID eventId, String title, OffsetDateTime start,
            @Schema(description = "Name of the venue") String venue,
            @Schema(description = "Town of the venue") String town,
            boolean cancelled,
            @Schema(description = "The match day is over") boolean over,
            @Schema(description = "Meeting point and venue are set, so drivers can be entered (DRV-01)") boolean ready,
            List<DriverRules.Missing> missing,
            @Schema(description = "One-way distance, if a route is known") Double distanceKm,
            Integer durationMinutes,
            @Schema(description = "Drivers can be entered right now by the caller") boolean changeable,
            List<DriverListDto.CarDto> cars,
            @Schema(description = "Free seats in all cars") int freeSeats) {
    }

    /** How often a member drives in the season (DRV-17): done and planned. */
    @Schema(name = "DriverShare")
    public record Share(PersonSummary person,
                        @Schema(description = "Trips on match days that are over") int trips,
                        @Schema(description = "Trips on upcoming match days") int plannedTrips,
                        @Schema(description = "Credited kilometres of the trips that are over") double kilometres,
                        @Schema(description = "Kilometres of the planned trips with a known distance") double plannedKilometres) {
    }

    /** Request: enter one driver for several match days at once (DRV-14). */
    @Schema(name = "DriverPlanInput")
    public record Input(
            @Schema(description = "Driver; missing: the caller herself") UUID driverId,
            @NotNull(message = DriverRules.SEATS_TEXT) @Min(value = DriverRules.MIN_SEATS, message = DriverRules.SEATS_TEXT)
            @Max(value = DriverRules.MAX_SEATS, message = DriverRules.SEATS_TEXT) Integer seats,
            @NotEmpty(message = "Bitte mindestens einen Spieltag wählen.")
            @Size(max = 60, message = "Höchstens 60 Spieltage auf einmal.") List<@NotNull UUID> eventIds) {
    }
}
