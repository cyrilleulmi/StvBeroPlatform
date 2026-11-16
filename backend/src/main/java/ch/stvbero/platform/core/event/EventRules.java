package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamRole;
import ch.stvbero.platform.core.clock.BackendClock;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Fachregeln zu Terminen und Serien (TER). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class EventRules {

    /** Standard-Antwortfrist in Stunden vor Beginn (TER-10, Annahme zu O-11). */
    public static final int DEFAULT_RESPONSE_DEADLINE = 24;

    /** Längste Antwortfrist: zwei Wochen. */
    public static final int MAX_RESPONSE_DEADLINE = 336;
    public static final String MIN_RESPONSE_DEADLINE_TEXT = "Mindestens 0 Stunden.";
    public static final String MAX_RESPONSE_DEADLINE_TEXT = "Höchstens 336 Stunden (14 Tage).";

    /** Längster Gültigkeitszeitraum einer Serie in Tagen, damit nicht versehentlich Jahre erzeugt werden. */
    public static final int MAX_SERIES_DAYS = 366;

    /** Uhrzeit «18:30». */
    public static final String TIME_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Ein Zeitraum von Tag bis Tag, beide eingeschlossen. */
    public record Period(LocalDate startDate, LocalDate endDate) {
        public boolean contains(LocalDate day) {
            return !day.isBefore(startDate) && !day.isAfter(endDate);
        }
    }

    /** Was mit einem künftigen Serientermin geschieht, der in eine neue Ausnahme fällt (TER-07, O-19). */
    public enum Outcome {
        /** Ohne Antworten: der Termin verschwindet. */
        REMOVE,
        /** Mit Antworten: der Termin wird abgesagt, Grund = Grund der Ausnahme. So geht keine Antwort verloren. */
        CANCEL
    }

    /** Zustand eines bestehenden künftigen Serientermins beim Abgleich mit der geänderten Serie. */
    public record Existing(boolean planned, boolean individuallyChanged) {
    }

    /**
     * Ergebnis des Abgleichs einer geänderten Serie mit ihren künftigen Terminen (TER-08).
     *
     * @param erzeugen     Daten, für die ein neuer Termin entsteht
     * @param uebernehmen  Daten, deren Termin die neuen Angaben der Serie übernimmt
     * @param wegfallen    Daten, deren Termin nicht mehr zur Serie passt (entfernen bzw. absagen, siehe {@link Folge})
     */
    public record SuSync(List<LocalDate> create, List<LocalDate> apply, List<LocalDate> drop) {
    }

    private EventRules() {
    }

    // ------------------------------------------------------------------ Rechte

    /**
     * Termine und Serien erfassen: Admins oder Coach in mindestens einem der Teams (TER-11, RR-03, RR-06).
     * So kann die Coach von Team 1 ein gemeinsames Training mit Team 2 erfassen (TER-01).
     */
    public static void checkCanCreate(boolean admin, Collection<TeamRole> teamRolesInTheTeams) {
        if (!canEdit(admin, teamRolesInTheTeams)) {
            throw DomainError.forbidden();
        }
    }

    /** Bearbeiten und absagen: Admins oder Coach eines beteiligten Teams (TER-11, RR-06). */
    public static boolean canEdit(boolean admin, Collection<TeamRole> teamRolesInTheTeams) {
        return admin || teamRolesInTheTeams.contains(TeamRole.COACH);
    }

    /** Ansehen: Mitglieder eines beteiligten Teams (jede Funktion) und Admins (RR-02, RR-07). */
    public static void checkCanView(boolean admin, Collection<TeamRole> teamRolesInTheTeams) {
        if (!admin && teamRolesInTheTeams.isEmpty()) {
            throw DomainError.forbidden();
        }
    }

    // ------------------------------------------------------------------ Zustand

    /** Abgesagte und vergangene Termine lassen sich nicht mehr ändern oder absagen (409, TER-06). */
    public static void checkChangeable(EventStatus status, Instant end, Instant now) {
        if (status == EventStatus.CANCELLED) {
            throw DomainError.conflict("Termin abgesagt", "Der Termin ist abgesagt und lässt sich nicht mehr ändern.", "TER-06");
        }
        if (!end.isAfter(now)) {
            throw DomainError.conflict("Termin vorbei", "Der Termin ist vorbei und lässt sich nicht mehr ändern.", "TER-06");
        }
    }

    /** Eine geänderte Beginnzeit gilt als Verschiebung (TER-06). Einmal verschoben, bleibt der Hinweis. */
    public static boolean rescheduled(boolean previous, Instant oldStart, Instant newStart) {
        return previous || !oldStart.equals(newStart);
    }

    /** Antwortfrist: X Stunden vor Beginn (TER-09). */
    public static Instant responseDeadline(Instant start, int hours) {
        return start.minus(Duration.ofHours(hours));
    }

    /** Ohne Angabe gilt die Standard-Antwortfrist von 24 Stunden (TER-10). */
    public static int responseDeadline(Integer hours) {
        return hours == null ? DEFAULT_RESPONSE_DEADLINE : hours;
    }

    // ------------------------------------------------------------------ Eingaben

    /** Ein Termin, der schon vorbei ist, wird weder erfasst noch dorthin verschoben. */
    public static void checkNotOver(Instant end, Instant now) {
        if (!end.isAfter(now)) {
            throw DomainError.invalidField("end", "Der Termin liegt in der Vergangenheit.");
        }
    }

    public static void checkTime(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw DomainError.invalidField("end", "Das Ende muss nach dem Beginn liegen.");
        }
    }

    /** Ein Spieltag gehört zu genau einem Team (AUF-03, O-14). */
    public static void checkTeams(EventType type, int countTeams) {
        if (type == EventType.MATCH_DAY && countTeams != 1) {
            throw DomainError.invalidField("teamIds", "Ein Spieltag gehört zu genau einem Team.");
        }
    }

    /** Ein Spieltag mit Spielen oder Aufgebot bleibt ein Spieltag (409, AUF-01). */
    public static void checkTypeChange(EventType previous, EventType newValue, boolean hasMatchesOrCallUp) {
        if (previous == EventType.MATCH_DAY && newValue != EventType.MATCH_DAY && hasMatchesOrCallUp) {
            throw DomainError.conflict("Spieltag", "Ein Spieltag mit Spielen oder Aufgebot bleibt ein Spieltag.", "AUF-01");
        }
    }

    public static void checkSeries(LocalTime start, LocalTime end, LocalDate startDate, LocalDate endDate) {
        if (!end.isAfter(start)) {
            throw DomainError.invalidField("endTime", "Das Ende muss nach dem Beginn liegen.");
        }
        if (endDate.isBefore(startDate)) {
            throw DomainError.invalidField("validUntil", "Das Ende der Serie muss nach ihrem Beginn liegen.");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_SERIES_DAYS) {
            throw DomainError.invalidField("validUntil", "Eine Serie dauert höchstens ein Jahr.");
        }
    }

    public static void checkException(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw DomainError.invalidField("endDate", "Das Ende der Ausnahme muss nach ihrem Beginn liegen.");
        }
    }

    // ------------------------------------------------------------------ Serien

    /**
     * Alle Daten einer Serie: jeder {@code tag} im Gültigkeitszeitraum, ohne die Daten in einer Ausnahme
     * (TER-05, TER-07). Aufsteigend sortiert.
     */
    public static List<LocalDate> dates(DayOfWeek day, LocalDate startDate, LocalDate endDate, Collection<Period> exceptions) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = startDate.with(TemporalAdjusters.nextOrSame(day)); !d.isAfter(endDate); d = d.plusWeeks(1)) {
            if (!inException(d, exceptions)) {
                dates.add(d);
            }
        }
        return dates;
    }

    public static boolean inException(LocalDate day, Collection<Period> exceptions) {
        return exceptions.stream().anyMatch(a -> a.contains(day));
    }

    /** Zeitpunkt eines Serientermins in der Zeitzone der Fachlogik (Sommer- und Winterzeit richtig). */
    public static Instant instant(LocalDate day, LocalTime time) {
        return day.atTime(time).atZone(BackendClock.ZONE).toInstant();
    }

    /** Neue Ausnahme: Termin ohne Antworten entfernen, mit Antworten absagen (TER-07, zustaende.md). */
    public static Outcome onException(boolean hasResponses) {
        return hasResponses ? Outcome.CANCEL : Outcome.REMOVE;
    }

    /**
     * Abgleich nach einer Änderung der Serie (TER-08). Betrachtet werden nur künftige Daten.
     * Einzeln geänderte oder abgesagte Termine bleiben, wie sie sind (TER-06). Für ein Datum, das schon
     * einen Termin hat (auch einen abgesagten), entsteht kein zweiter.
     *
     * @param bestehend künftige Termine der Serie nach ihrem Seriendatum
     * @param soll      künftige Daten der geänderten Serie
     */
    public static SuSync sync(Map<LocalDate, Existing> existing, Collection<LocalDate> target) {
        Set<LocalDate> targetDates = new TreeSet<>(target);
        List<LocalDate> create = new ArrayList<>();
        List<LocalDate> apply = new ArrayList<>();
        List<LocalDate> drop = new ArrayList<>();
        for (LocalDate d : targetDates) {
            Existing b = existing.get(d);
            if (b == null) {
                create.add(d);
            } else if (b.planned() && !b.individuallyChanged()) {
                apply.add(d);
            }
        }
        new TreeSet<>(existing.keySet()).forEach(d -> {
            Existing b = existing.get(d);
            if (!targetDates.contains(d) && b.planned() && !b.individuallyChanged()) {
                drop.add(d);
            }
        });
        return new SuSync(create, apply, drop);
    }

    // ------------------------------------------------------------------ Anzeige

    /** Anzahl Spielerinnen pro Antwortstatus. Jede Spielerin zählt einmal, auch in mehreren Teams. */
    public static CountsDto count(Collection<ResponseStatus> responsesThePlayers) {
        int attending = 0;
        int declined = 0;
        int maybe = 0;
        int pending = 0;
        for (ResponseStatus s : responsesThePlayers) {
            switch (s) {
                case ATTENDING -> attending++;
                case DECLINED -> declined++;
                case MAYBE -> maybe++;
                case PENDING -> pending++;
            }
        }
        return new CountsDto(attending, declined, maybe, pending);
    }

    /** Kalendertag eines Zeitpunkts in der Zeitzone der Fachlogik. */
    public static LocalDate date(Instant instant) {
        return instant.atZone(BackendClock.ZONE).toLocalDate();
    }

    public static String time(LocalTime time) {
        return time.format(TIME);
    }

    public static LocalTime time(String time) {
        return LocalTime.parse(time, TIME);
    }

    public static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
