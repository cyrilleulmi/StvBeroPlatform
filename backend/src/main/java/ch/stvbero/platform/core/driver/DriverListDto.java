package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.person.PersonSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
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
        @Schema(description = "Members of the teams of the match day, by name; only for coaches and admins") List<PersonSummary> members,
        @Schema(description = "People who travel on their own and need no seat, by name (DRV-20)") List<PersonSummary> individual,
        @Schema(description = "Seats for passengers the caller usually offers: her places minus herself (DRV-18)") int defaultSeats) {

    /** A car with its driver and passengers (DRV-02, DRV-03). */
    @Schema(name = "Car")
    public record CarDto(UUID id, PersonSummary driver, int seats, int freeSeats, List<PersonSummary> passengers,
                         @Schema(description = "Credited kilometres for the return trip; missing without distance or if cancelled (DRV-05, DRV-10)") Double creditKm) {
    }

    /** Route from the meeting point to the venue (DRV-05, DRV-07). */
    @Schema(name = "Route")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RouteDto(RouteStatus status, String from, String to, Double distanceKm,
                           @Schema(description = "Driving time in use: overwritten by hand, otherwise calculated (DRV-21)") Integer durationMinutes,
                           @Schema(description = "Credit per driver: twice the distance; missing without distance") Double creditKm,
                           @Schema(description = "Driving time as calculated by the service (DRV-22)") Integer calculatedMinutes,
                           @Schema(description = "The driving time was overwritten by hand (DRV-21)") boolean durationManual,
                           @Schema(description = "Who overwrote the driving time") PersonSummary durationChangedBy,
                           @Schema(description = "When the driving time was overwritten") OffsetDateTime durationChangedAt,
                           @Schema(description = "Service that calculated the route; missing for kilometres entered by hand (DRV-22)") RouteProvider provider,
                           @Schema(description = "When the route was calculated or entered (DRV-22)") OffsetDateTime calculatedAt,
                           @Schema(description = "Day and time of departure were taken into account (traffic); the services the app uses calculate without (DRV-22)") boolean departureTimeUsed) {
    }
}
