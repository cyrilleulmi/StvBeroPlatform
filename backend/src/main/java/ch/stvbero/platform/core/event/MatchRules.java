package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.error.DomainError;

import java.time.Instant;
import java.util.Objects;

/** Fachregeln zu Spielen eines Spieltags (AUF-14, AUF-15, SUH-06). Ohne Datenbank. */
public final class MatchRules {

    /** Resultat Heim:Gast, z.B. «5:3». */
    public static final String RESULT_PATTERN = "^\\d{1,2}:\\d{1,2}$";
    public static final String RESULT_TEXT = "Resultat bitte als Heim:Gast, z.B. 5:3.";
    public static final String ON_MATCH_DAY = "Das Spiel muss am Tag des Spieltags stattfinden.";
    public static final String IMPORTED = "Gegner, Anspielzeit und Heim/Auswärts kommen von swiss unihockey und lassen sich nicht ändern.";
    public static final String NOT_DELETABLE = "Spiele von swiss unihockey lassen sich nur löschen, wenn swiss unihockey sie nicht mehr liefert.";
    public static final String ONLY_MATCH_DAY = "Nur ein Spieltag hat Spiele.";
    public static final String IMPORTED_RESCHEDULE = "Der Spieltag hat Spiele von swiss unihockey. Er lässt sich nur am selben Tag verschieben; das Datum kommt von swiss unihockey.";
    public static final String CANCELLED = "Der Spieltag ist abgesagt. Spiele lassen sich nicht mehr ändern.";

    private MatchRules() {
    }

    /** Spiele gibt es nur bei Spieltagen (AUF-01, AUF-14). */
    public static void checkMatchDay(EventType type) {
        if (type != EventType.MATCH_DAY) {
            throw DomainError.conflict("Kein Spieltag", ONLY_MATCH_DAY, "AUF-01");
        }
    }

    /**
     * Beim Ändern eines Spiels gilt die Tagesregel nur für eine neue Anspielzeit. Ein Resultat lässt sich damit
     * immer erfassen (AUF-15), auch wenn ein früherer Stand die Regel nicht erfüllt.
     */
    public static void checkOnMatchDayWhenChanging(Instant startTimeOld, Instant startTimeNew, Instant startMatchDay) {
        if (!startTimeOld.equals(startTimeNew)) {
            checkOnMatchDay(startTimeNew, startMatchDay);
        }
    }

    /**
     * Ein Spieltag mit Spielen aus swiss unihockey lässt sich nicht auf einen anderen Tag verschieben: Datum und
     * Zeit der Spiele kommen von swiss unihockey (SUH-05, SUH-06), sie ziehen nicht mit (O-48). 422 beim Feld
     * {@code beginn}.
     */
    public static void checkReschedulable(boolean hasImportedMatches, Instant startOld, Instant startNew) {
        if (hasImportedMatches && !EventRules.date(startOld).equals(EventRules.date(startNew))) {
            throw DomainError.ruleViolation("start", "Spiele von swiss unihockey", IMPORTED_RESCHEDULE, "SUH-05");
        }
    }

    /** Ein Spieltag findet an einem Tag statt (AUF-01): Spiele liegen am Datum seines Beginns. */
    public static void checkOnMatchDay(Instant startTime, Instant startMatchDay) {
        if (!EventRules.date(startTime).equals(EventRules.date(startMatchDay))) {
            throw DomainError.invalidField("startTime", ON_MATCH_DAY);
        }
    }

    /**
     * Bei Spielen aus swiss unihockey sind Gegner, Anspielzeit und Heim/Auswärts schreibgeschützt (409). Das
     * Resultat lässt sich erfassen (AUF-15); der Abgleich kann es später überschreiben.
     */
    public static void checkChangeable(MatchSource source, String opponentOld, Instant startTimeOld, boolean homeOld,
                                       String opponentNew, Instant startTimeNew, boolean homeNew) {
        if (source == MatchSource.SWISS_UNIHOCKEY && (!Objects.equals(opponentOld, opponentNew)
                || !startTimeOld.equals(startTimeNew) || homeOld != homeNew)) {
            throw DomainError.conflict("Importiertes Spiel", IMPORTED, "SUH-06");
        }
    }

    /** Von Hand erfasste Spiele lassen sich löschen, importierte erst, wenn SU sie nicht mehr liefert (409). */
    public static void checkDeletable(MatchSource source, boolean missingAtSu) {
        if (source == MatchSource.SWISS_UNIHOCKEY && !missingAtSu) {
            throw DomainError.conflict("Importiertes Spiel", NOT_DELETABLE, "SUH-06");
        }
    }

    /** Ein abgesagter Spieltag nimmt keine Änderungen an Spielen an (409, TER-06). */
    public static void checkNotCancelled(EventStatus status) {
        if (status == EventStatus.CANCELLED) {
            throw DomainError.conflict("Termin abgesagt", CANCELLED, "TER-06");
        }
    }
}
