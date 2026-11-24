package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.person.PersonSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
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
        @Schema(description = "Members of the team, by name; only for coaches and admins") List<PersonSummary> members,
        @Schema(description = "Who can drive and with how many places, by name (DRV-18)") List<Driver> drivers,
        @Schema(description = "Sum of the distribution (DRV-27)") Totals totals,
        @Schema(description = "Open swap requests the caller sees: her own and those to her; coaches and admins all (DRV-25)") List<Swap> swaps,
        @Schema(description = "Other trips of the team in the season, by date (DRV-28)") List<ExtraTrip> extraTrips,
        @Schema(description = "Changes of the plan, newest first (DRV-26)") List<HistoryEntry> history,
        @Schema(description = "People expected to travel to a match day of this team (DRV-29)") int expected,
        @Schema(description = "All players and coaches of the team: the number without a manual one (DRV-29)") int expectedDefault,
        @Schema(description = "The number of the team was set by hand (DRV-29)") boolean expectedManual) {

    static final String EXPECTED_TEXT = "Bitte eine Zahl von 1 bis 60 angeben.";

    /** Request: the assumed number of people who travel; {@code null} goes back to the default (DRV-29). */
    @Schema(name = "DriverPlanExpectedInput")
    public record ExpectedInput(
            @Min(value = 1, message = EXPECTED_TEXT) @Max(value = 60, message = EXPECTED_TEXT) Integer expected) {
    }

    /** Request: the next driver in line takes a trip (DRV-31). */
    @Schema(name = "DriverPlanNextInput")
    public record NextInput(
            @Schema(description = "Driver who hands her trip over; missing: one more car is entered") UUID replaces) {
    }

    /**
     * A fair distribution of the trips that are still missing, as a proposal (DRV-30): nothing is entered yet.
     * Drivers who are entered stay as they are.
     */
    @Schema(name = "DriverPlanDistribution")
    public record Distribution(
            @Schema(description = "Proposed trips, by match day and then by name") List<ProposedTrip> trips) {
    }

    /** A trip the distribution proposes. */
    @Schema(name = "DriverPlanProposedTrip")
    public record ProposedTrip(UUID eventId, PersonSummary driver,
                               @Schema(description = "Seats for passengers: the places of her car minus herself (DRV-18)") int seats) {
    }

    /** Request: enter the proposed trips (DRV-30). They are entered as they are and stay. */
    @Schema(name = "DriverPlanDistributeInput")
    public record DistributeInput(
            @NotNull(message = "Bitte den Vorschlag angeben.")
            @Size(max = 600, message = "Zu viele Fahrten auf einmal.") List<@NotNull @Valid TripEntry> trips) {
    }

    /** One trip of a distribution that is entered. */
    @Schema(name = "DriverPlanTripEntry")
    public record TripEntry(
            @NotNull(message = "Bitte den Spieltag angeben.") UUID eventId,
            @NotNull(message = "Bitte angeben, wer fährt.") UUID driverId,
            @NotNull(message = DriverRules.SEATS_TEXT) @Min(value = DriverRules.MIN_SEATS, message = DriverRules.SEATS_TEXT)
            @Max(value = DriverRules.MAX_SEATS, message = DriverRules.SEATS_TEXT) Integer seats) {
    }

    /** Sum of all shares (DRV-27). */
    @Schema(name = "DriverPlanTotals")
    public record Totals(int trips, int plannedTrips, double kilometres, double plannedKilometres) {
    }

    /** An open request to take over or swap a trip (DRV-25). */
    @Schema(name = "DriverSwap")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Swap(UUID id,
                       @Schema(description = "Match day whose trip is given away") UUID eventId,
                       @Schema(description = "«Spieltag So 22.11.»") String eventLabel,
                       @Schema(description = "Driver who gives the trip away") PersonSummary requester,
                       @Schema(description = "Who is asked to take it over") PersonSummary partner,
                       @Schema(description = "Match day of the partner's trip in return; missing: takeover") UUID partnerEventId,
                       String partnerEventLabel,
                       OffsetDateTime createdAt,
                       @Schema(description = "The caller may accept or decline (the partner, coaches, admins)") boolean canAnswer,
                       @Schema(description = "The caller may withdraw (the requester, coaches, admins)") boolean canWithdraw) {
    }

    /** A trip without an event in the app (DRV-28). */
    @Schema(name = "ExtraTrip")
    public record ExtraTrip(UUID id, LocalDate date, String title, PersonSummary driver,
                            @Schema(description = "Kilometres one way") double distanceKm,
                            @Schema(description = "Credited kilometres: there and back") double creditKm,
                            @Schema(description = "The date has passed") boolean over,
                            @Schema(description = "The caller may remove it: the driver, who added it, coaches, admins") boolean canRemove) {
    }

    /** One change of the plan (DRV-26). */
    @Schema(name = "DriverPlanHistoryEntry")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryEntry(UUID id, @Schema(description = "Match day it concerns; missing for other trips") UUID eventId,
                               DriverPlanHistory.Kind kind,
                               @Schema(description = "German sentence for display") String text,
                               @Schema(description = "Who made the change") PersonSummary by,
                               OffsetDateTime at) {
    }

    /** Request: give away the trip of a match day (DRV-25). */
    @Schema(name = "DriverSwapInput")
    public record SwapInput(
            @NotNull(message = "Bitte den Spieltag wählen.") UUID eventId,
            @Schema(description = "Driver who gives the trip away; missing: the caller herself") UUID driverId,
            @NotNull(message = "Bitte wählen, wer die Fahrt übernimmt.") UUID partnerId,
            @Schema(description = "Match day on which the partner drives and the driver takes over in return; missing: takeover") UUID partnerEventId,
            @Schema(description = "`true`: enter right away; `false`: ask the partner first") boolean direct) {
    }

    /** Request: add another trip (DRV-28). */
    @Schema(name = "ExtraTripInput")
    public record TripInput(
            @NotNull(message = "Bitte das Datum angeben.") LocalDate date,
            @NotBlank(message = "Bitte angeben, wofür die Fahrt war.")
            @Size(max = 80, message = "Höchstens 80 Zeichen.") String title,
            @Schema(description = "Driver; missing: the caller herself") UUID driverId,
            @NotNull(message = ExtraTrips.DISTANCE_TEXT) @DecimalMin(value = "0.1", message = ExtraTrips.DISTANCE_TEXT)
            @DecimalMax(value = "2000", message = ExtraTrips.DISTANCE_TEXT) Double distanceKm) {
    }

    /** A member of the team with her driving settings (DRV-18). */
    @Schema(name = "DriverSetting")
    public record Driver(PersonSummary person, boolean canDrive,
                         @Schema(description = "Places in her car, herself included") int carSeats,
                         @Schema(description = "The caller may change this entry: herself, or everyone as coach or admin") boolean canChange) {
    }

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
            @Schema(description = "Credit of one trip, there and back, to the meter (DRV-05); missing without distance or if cancelled") Double creditKm,
            Integer durationMinutes,
            @Schema(description = "Drivers can be entered right now by the caller") boolean changeable,
            List<DriverListDto.CarDto> cars,
            @Schema(description = "Free seats in all cars") int freeSeats,
            @Schema(description = "People expected to travel: the number of the match day, otherwise of the team (DRV-29)") int expected,
            @Schema(description = "The number was set by hand for this match day (DRV-29)") boolean expectedManual,
            @Schema(description = "Places of all cars, drivers included") int places,
            @Schema(description = "People who responded «dabei»") int attending,
            @Schema(description = "The meeting point is the venue: nobody has to drive (DRV-30)") boolean noTrip) {
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
