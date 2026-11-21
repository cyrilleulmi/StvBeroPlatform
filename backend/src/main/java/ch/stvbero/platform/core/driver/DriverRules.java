package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.EventStatus;
import ch.stvbero.platform.core.event.EventType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Rules of the driver list (DRV). Pure, without database, so that they can be unit tested. */
public final class DriverRules {

    static final String DRIVER_IS_INDIVIDUAL = "ist als Fahrerin oder Fahrer eingetragen. Zuerst das Auto entfernen.";
    public static final String NOTHING_TO_PROPOSE = "Der Vorschlag ändert nichts: Alle haben schon einen Platz.";

    public static final int MIN_SEATS = 1;
    public static final int MAX_SEATS = 8;
    public static final String SEATS_TEXT = "Bitte eine Zahl von 1 bis 8 eingeben.";
    public static final String DISTANCE_TEXT = "Bitte eine Zahl von 0.1 bis 1000 eingeben.";
    /** An unavailable routing service is asked again on the next view after this time (DRV-07). */
    public static final Duration RETRY_AFTER = Duration.ofMinutes(10);

    static final String ONLY_MATCH_DAY = "Nur ein Spieltag hat eine Fahrerliste.";
    static final String NOT_READY = "Die Fahrerliste ist erst bereit, wenn Treffpunkt und Spielort erfasst sind.";
    static final String CANCELLED = "Der Spieltag ist abgesagt. Die Fahrerliste lässt sich nicht mehr ändern.";
    static final String OVER = "Der Spieltag ist vorbei. Die Fahrerliste können nur noch Coaches und Admins ändern.";
    static final String NOT_MEMBER = "Fahren und mitfahren können nur Mitglieder des Teams.";
    static final String ALREADY_DRIVING = "fährt an diesem Spieltag schon ein Auto.";
    static final String DRIVER_IS_SEATED = "Wer fährt, sitzt schon im eigenen Auto.";
    static final String FULL = "Dieses Auto ist schon voll.";
    static final String CAR_NOT_FOUND = "Dieses Auto gibt es in der Fahrerliste nicht.";
    static final String NOT_IN_CAR = "Diese Person fährt in diesem Auto nicht mit.";

    /** What is missing before the driver list can be used (DRV-01). */
    public enum Missing {
        MEETING_POINT,
        VENUE
    }

    private DriverRules() {
    }

    // ------------------------------------------------------------------ When and who

    /** Only match days have a driver list (DRV-01). */
    public static void checkMatchDay(EventType type) {
        if (type != EventType.MATCH_DAY) {
            throw DomainError.conflict("Kein Spieltag", ONLY_MATCH_DAY, "DRV-01");
        }
    }

    /** Meeting point and venue are needed (DRV-01); empty list: usable. */
    public static List<Missing> missing(boolean hasMeetingPoint, boolean hasVenue) {
        List<Missing> missing = new ArrayList<>();
        if (!hasMeetingPoint) {
            missing.add(Missing.MEETING_POINT);
        }
        if (!hasVenue) {
            missing.add(Missing.VENUE);
        }
        return missing;
    }

    /**
     * The list can be changed (DRV-01, DRV-10): usable, not cancelled; after the end of the match day only coaches
     * and admins still correct it.
     */
    public static void checkChangeable(List<Missing> missing, EventStatus status, Instant end, Instant now,
                                       boolean canManage) {
        if (!missing.isEmpty()) {
            throw DomainError.conflict("Fahrerliste nicht bereit", NOT_READY, "DRV-01");
        }
        if (status == EventStatus.CANCELLED) {
            throw DomainError.conflict("Spieltag abgesagt", CANCELLED, "DRV-10");
        }
        if (!end.isAfter(now) && !canManage) {
            throw DomainError.conflict("Spieltag vorbei", OVER, "DRV-02");
        }
    }

    /**
     * For oneself every member of the team; for someone else only coaches and admins (DRV-02, DRV-03, RR-07).
     */
    public static void checkCanActFor(UUID caller, UUID person, boolean callerIsMember, boolean canManage) {
        boolean self = caller.equals(person);
        if (self ? !(callerIsMember || canManage) : !canManage) {
            throw DomainError.forbidden();
        }
    }

    /** Drivers and passengers are members of a participating team (any team role), DRV-02, DRV-03. */
    public static void checkMember(boolean member, String field) {
        if (!member) {
            throw DomainError.ruleViolation(field, "Nicht im Team", NOT_MEMBER, "DRV-02");
        }
    }

    /** A person drives at most one car per match day (DRV-02). */
    public static void checkNotDriving(boolean alreadyDriving, String name) {
        if (alreadyDriving) {
            throw DomainError.conflict("Fährt schon", name + " " + ALREADY_DRIVING, "DRV-02");
        }
    }

    /** A driver is not a passenger in another car (DRV-03). */
    public static void checkNotDriver(boolean isDriver) {
        if (isDriver) {
            throw DomainError.ruleViolation("personId", "Fährt selbst", DRIVER_IS_SEATED, "DRV-03");
        }
    }

    /** A car never has more passengers than seats (DRV-03). */
    public static void checkFreeSeat(int seats, int passengers) {
        if (passengers >= seats) {
            throw DomainError.conflict("Auto voll", FULL, "DRV-03");
        }
    }

    /** Seats of a car: 1 to 8, and at least as many as passengers sitting in it (DRV-02, DRV-03). */
    public static void checkSeats(int seats, int passengers) {
        if (seats < MIN_SEATS || seats > MAX_SEATS) {
            throw DomainError.invalidField("seats", SEATS_TEXT);
        }
        if (seats < passengers) {
            throw DomainError.ruleViolation("seats", "Zu wenig Plätze", "Im Auto sitzen schon " + passengers
                    + (passengers == 1 ? " Person" : " Personen") + ". Weniger Plätze gehen nicht.", "DRV-03");
        }
    }

    /** A driver cannot travel on her own until her car is removed (DRV-20). */
    public static void checkNotDrivingForIndividual(boolean isDriver, String name) {
        if (isDriver) {
            throw DomainError.conflict("Fährt selbst", name + " " + DRIVER_IS_INDIVIDUAL, "DRV-20");
        }
    }

    // ------------------------------------------------------------------ Who has no seat

    /**
     * Who still needs a seat (DRV-04): with a published call-up the called-up players, otherwise the players who
     * responded «dabei»; without those who drive or ride along. In the order of {@code candidates}.
     */
    public static List<UUID> withoutSeat(boolean callUpPublished, Collection<UUID> calledUp,
                                         Collection<UUID> attendingPlayers, Set<UUID> seated) {
        Set<UUID> candidates = new LinkedHashSet<>(callUpPublished ? calledUp : attendingPlayers);
        candidates.removeAll(seated);
        return List.copyOf(candidates);
    }

    // ------------------------------------------------------------------ Route and credits

    /**
     * Whether the route must be calculated (DRV-05, DRV-07, DRV-08): never calculated, meeting point or venue
     * changed, or the service was unavailable at the last attempt longer than {@link #RETRY_AFTER} ago. A route
     * found, not found or entered by hand stays as it is for the same places.
     */
    public static boolean needsCalculation(String storedPlaces, RouteStatus status, Instant attemptedAt,
                                           String places, Instant now) {
        if (storedPlaces == null || !storedPlaces.equals(places)) {
            return true;
        }
        return status == RouteStatus.UNAVAILABLE && !attemptedAt.plus(RETRY_AFTER).isAfter(now);
    }

    /** Kilometres with one decimal (23400 m → 23.4). */
    public static Double kilometres(Integer meters) {
        return meters == null ? null : Math.round(meters / 100.0) / 10.0;
    }

    /** Meters from kilometres entered by hand (DRV-07). */
    public static int meters(double kilometres) {
        if (!(kilometres >= 0.1 && kilometres <= 1000)) {
            throw DomainError.invalidField("distanceKm", DISTANCE_TEXT);
        }
        return (int) Math.round(kilometres * 1000);
    }

    /** Driving time in whole minutes, at least 1 for a route with a duration. */
    public static Integer minutes(Integer seconds) {
        return seconds == null ? null : Math.max(1, (int) Math.round(seconds / 60.0));
    }

    /**
     * Credit of a driver in meters (DRV-05, DRV-10): the return trip, twice the distance; nothing for a cancelled
     * match day or without distance.
     */
    public static Integer credit(Integer distanceMeters, EventStatus status) {
        if (distanceMeters == null || status == EventStatus.CANCELLED) {
            return null;
        }
        return distanceMeters * 2;
    }

    // ------------------------------------------------------------------ Season summary

    /** One trip of a driver: match day, status, distance of the route ({@code null} if unknown). */
    public record Trip(UUID eventId, UUID driverId, EventStatus status, Integer distanceMeters) {
    }

    /** Sum per person (DRV-09). */
    public record Total(int trips, int creditMeters, int tripsWithoutDistance) {
    }

    /**
     * Trips and credited meters per driver (DRV-09). Cancelled match days count neither as a trip nor with
     * kilometres (DRV-10); a trip without known distance counts as a trip without kilometres.
     */
    public static Map<UUID, Total> totals(Collection<Trip> trips) {
        Map<UUID, Total> totals = new HashMap<>();
        for (Trip trip : trips) {
            if (trip.status() == EventStatus.CANCELLED) {
                continue;
            }
            Integer credit = credit(trip.distanceMeters(), trip.status());
            Total before = totals.getOrDefault(trip.driverId(), new Total(0, 0, 0));
            totals.put(trip.driverId(), new Total(before.trips() + 1,
                    before.creditMeters() + (credit == null ? 0 : credit),
                    before.tripsWithoutDistance() + (credit == null ? 1 : 0)));
        }
        return totals;
    }

    // ------------------------------------------------------------------ Notification texts (DRV-11)

    public static String passengerJoinedText(String passenger, String event) {
        return passenger + " fährt am " + event + " bei dir mit.";
    }

    public static String passengerLeftText(String passenger, String event) {
        return passenger + " fährt am " + event + " nicht mehr bei dir mit.";
    }

    public static String seatAssignedText(String driver, String event) {
        return "Du fährst am " + event + " bei " + driver + " mit.";
    }

    public static String seatRemovedText(String driver, String event) {
        return "Du fährst am " + event + " nicht mehr bei " + driver + " mit. Du hast noch keinen anderen Platz.";
    }

    public static String carRemovedText(String driver, String event) {
        return driver + " fährt am " + event + " nicht. Du hast keinen Platz mehr in der Fahrerliste.";
    }

    public static String carAddedText(String event, int seats) {
        return "Du bist am " + event + " als Fahrerin oder Fahrer eingetragen, mit " + seats
                + (seats == 1 ? " Platz." : " Plätzen.");
    }

    public static String ownCarRemovedText(String event) {
        return "Dein Auto ist am " + event + " nicht mehr in der Fahrerliste.";
    }

    public static String individualText(String event) {
        return "Du bist am " + event + " als «reist individuell an» eingetragen. Du brauchst keinen Platz in der Fahrerliste.";
    }

    public static String individualRemovedText(String event) {
        return "Du bist am " + event + " nicht mehr als «reist individuell an» eingetragen.";
    }

    public static String seatsChangedText(String event, int seats) {
        return "Dein Auto hat am " + event + " jetzt " + seats + (seats == 1 ? " Platz." : " Plätze.");
    }
}
