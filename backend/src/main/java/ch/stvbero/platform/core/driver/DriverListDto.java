package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.person.PersonSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Driver list of a match day (DRV-01 to DRV-10). {@code route} is missing until the list is usable;
 * {@code members} (the people who can be entered) only for coaches and admins.
 */
@Schema(name = "DriverList")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DriverListDto(
        UUID eventId,
        @Schema(description = "Meeting point and venue are set, so the list can be used (DRV-01)") boolean ready,
        @Schema(description = "What is missing before the list can be used (DRV-01)") List<DriverRules.Missing> missing,
        RouteDto route,
        @Schema(description = "Match day cancelled: list kept, no credits (DRV-10)") boolean cancelled,
        @Schema(description = "The caller can enter herself as driver or passenger right now") boolean canJoin,
        @Schema(description = "Coach or admin: enters and changes drivers and passengers for others, route by hand") boolean canManage,
        @Schema(description = "The call-up is published: «without seat» counts the called-up players (DRV-04)") boolean callUpPublished,
        List<CarDto> cars,
        @Schema(description = "Called-up players (or «dabei» without published call-up) without a seat, by name (DRV-04)") List<PersonSummary> withoutSeat,
        @Schema(description = "Members of the teams of the match day, by name; only for coaches and admins") List<PersonSummary> members) {

    /** A car with its driver and passengers (DRV-02, DRV-03). */
    @Schema(name = "Car")
    public record CarDto(UUID id, PersonSummary driver, int seats, int freeSeats, List<PersonSummary> passengers,
                         @Schema(description = "Credited kilometres for the return trip; missing without distance or if cancelled (DRV-05, DRV-10)") Double creditKm) {
    }

    /** Route from the meeting point to the venue (DRV-05, DRV-07). */
    @Schema(name = "Route")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RouteDto(RouteStatus status, String from, String to, Double distanceKm, Integer durationMinutes,
                           @Schema(description = "Credit per driver: twice the distance; missing without distance") Double creditKm) {
    }
}
