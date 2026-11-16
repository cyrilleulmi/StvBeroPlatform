package ch.stvbero.platform.kern.swissunihockey;

import ch.stvbero.platform.kern.termin.BenachrichtigungsRegeln;
import ch.stvbero.platform.kern.termin.TerminRegeln;
import ch.stvbero.platform.kern.uhr.Uhr;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Fachregeln des Abgleichs mit swiss unihockey (SUH-02 bis SUH-08). Ohne Datenbank, damit sie als Unit-Test
 * prüfbar sind; {@link Abgleich} wendet sie an.
 */
public final class AbgleichRegeln {

    /**
     * Wie lange ein Spiel dauert, um das Ende eines importierten Spieltags zu bestimmen: Ende = letzte Anspielzeit
     * plus diese Dauer (Annahme F-07, passt zu Termin 04 im Seed: 11:45 bis 13:00).
     */
    public static final Duration SPIELDAUER = Duration.ofMinutes(75);

    public static final String TITEL = "Spieltag";

    private static final String[] WOCHENTAGE = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("HH:mm");

    private AbgleichRegeln() {
    }

    // ------------------------------------------------------------------ Wer sind wir?

    /**
     * Name des eigenen Teams in den Spielen. swiss unihockey liefert die Spiele eines Teams nur mit den Namen von
     * Heim- und Gastteam. Das eigene Team ist das, das in jedem Spiel vorkommt. Kommen mehrere in jedem Spiel vor
     * (z.B. bei nur einem Spiel), entscheidet der Name des Teams in der App, sonst das Heimteam des ersten Spiels
     * (Annahme F-07, O-21).
     *
     * @return {@code null}, wenn es keine Spiele gibt
     */
    public static String eigenesTeam(List<SuSpiel> spiele, String teamName) {
        if (spiele.isEmpty()) {
            return null;
        }
        Set<String> inAllen = null;
        for (SuSpiel s : spiele) {
            Set<String> namen = new LinkedHashSet<>(List.of(norm(s.heimteam()), norm(s.gastteam())));
            if (inAllen == null) {
                inAllen = namen;
            } else {
                inAllen.retainAll(namen);
            }
        }
        if (inAllen.size() == 1) {
            return inAllen.iterator().next();
        }
        String eigener = norm(teamName == null ? "" : teamName);
        if (inAllen.contains(eigener)) {
            return eigener;
        }
        return inAllen.isEmpty() ? eigener : inAllen.iterator().next();
    }

    /** Gegner und Heim/Auswärts aus Sicht des eigenen Teams. */
    public record Seite(String gegner, boolean heim) {
    }

    public static Seite seite(SuSpiel s, String eigenesTeam) {
        boolean auswaerts = eigenesTeam != null && norm(s.gastteam()).equals(eigenesTeam)
                && !norm(s.heimteam()).equals(eigenesTeam);
        return auswaerts ? new Seite(kuerzen(s.heimteam(), 80), false) : new Seite(kuerzen(s.gastteam(), 80), true);
    }

    // ------------------------------------------------------------------ Spieltage bilden (SUH-04)

    /** Spiele am selben Tag in derselben Halle gehören zu einem Spieltag (SUH-04). */
    public record Schluessel(LocalDate tag, String halle) {

        public static Schluessel von(Instant anspielzeit, String halle) {
            return new Schluessel(TerminRegeln.datum(anspielzeit), norm(halle == null ? "" : halle));
        }
    }

    /** Spiele nach Tag und Halle gruppiert, Gruppen und Spiele nach Anspielzeit. */
    public static Map<Schluessel, List<SuSpiel>> gruppieren(Collection<SuSpiel> spiele) {
        Map<Schluessel, List<SuSpiel>> gruppen = new LinkedHashMap<>();
        spiele.stream()
                .sorted(Comparator.comparing(SuSpiel::anspielzeit).thenComparing(SuSpiel::suSpielId))
                .forEach(s -> gruppen.computeIfAbsent(Schluessel.von(s.anspielzeit(), s.halle()), k -> new ArrayList<>())
                        .add(s));
        return gruppen;
    }

    /** Beginn und Ende eines importierten Spieltags. */
    public record Zeitraum(Instant beginn, Instant ende) {
    }

    /**
     * Beginn ist die erste Anspielzeit (Annahme F-03), Ende die letzte plus {@link #SPIELDAUER}.
     */
    public static Zeitraum zeitraum(Collection<Instant> anspielzeiten) {
        Instant erste = anspielzeiten.stream().min(Comparator.naturalOrder()).orElseThrow();
        Instant letzte = anspielzeiten.stream().max(Comparator.naturalOrder()).orElseThrow();
        return new Zeitraum(erste, letzte.plus(SPIELDAUER));
    }

    /** Nur Spiele, die in die Saison fallen, werden übernommen. */
    public static boolean inSaison(Instant anspielzeit, LocalDate von, LocalDate bis) {
        LocalDate tag = TerminRegeln.datum(anspielzeit);
        return !tag.isBefore(von) && !tag.isAfter(bis);
    }

    // ------------------------------------------------------------------ Spielort (SUH-07)

    /**
     * Google-Maps-Link des Spielorts (SUH-07): der von swiss unihockey gelieferte, sonst aus den Koordinaten,
     * sonst eine Suche nach Halle und Ort.
     */
    public static String mapsUrl(SuSpiel s) {
        if (s.mapsUrl() != null && !s.mapsUrl().isBlank()) {
            return kuerzen(s.mapsUrl().strip(), 500);
        }
        String suche = s.breite() != null && s.laenge() != null
                ? s.breite() + "," + s.laenge()
                : s.halle().strip() + (s.ort() == null ? "" : " " + s.ort().strip());
        return kuerzen("https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(suche, StandardCharsets.UTF_8),
                500);
    }

    // ------------------------------------------------------------------ Benachrichtigung (BEN-06)

    /** Was an einem Spiel für die Spielerinnen zählt (SUH-05): Zeit, Halle, Gegner, Heim/Auswärts. */
    public record Stand(String gegner, Instant anspielzeit, boolean heim, String halle) {
    }

    /** Hat sich etwas geändert, worüber das Team benachrichtigt wird (SUH-05, BEN-06)? Das Resultat zählt nicht. */
    public static boolean wesentlichGeaendert(Stand alt, Stand neu) {
        return !alt.anspielzeit().equals(neu.anspielzeit()) || !alt.gegner().equals(neu.gegner())
                || alt.heim() != neu.heim() || !norm(Objects.toString(alt.halle(), "")).equals(norm(neu.halle()));
    }

    /**
     * Eine Änderung in Worten, z.B. «Spiel gegen Unihockey Beispielstadt neu um 12:15» oder «Spiel um 09:30 neu
     * gegen UHC Drei, neu in Turnhalle Weid».
     */
    public static String aenderung(Stand alt, Stand neu) {
        List<String> teile = new ArrayList<>();
        if (!alt.anspielzeit().equals(neu.anspielzeit())) {
            boolean andererTag = !TerminRegeln.datum(alt.anspielzeit()).equals(TerminRegeln.datum(neu.anspielzeit()));
            teile.add(andererTag ? "neu am " + zeitpunkt(neu.anspielzeit()) : "neu um " + zeit(neu.anspielzeit()));
        }
        if (!norm(Objects.toString(alt.halle(), "")).equals(norm(neu.halle()))) {
            teile.add("neu in " + neu.halle());
        }
        if (!alt.gegner().equals(neu.gegner())) {
            teile.add("neu gegen " + neu.gegner());
        }
        if (alt.heim() != neu.heim()) {
            teile.add(neu.heim() ? "neu als Heimspiel" : "neu als Auswärtsspiel");
        }
        String spiel = alt.gegner().equals(neu.gegner())
                ? "Spiel gegen " + neu.gegner()
                : "Spiel um " + zeit(alt.anspielzeit());
        return spiel + " " + String.join(", ", teile);
    }

    /** «Spieltag So 22.11. wurde geändert: Spiel gegen Unihockey Beispielstadt neu um 12:15.» */
    public static String textSpielGeaendert(String titel, Instant alterBeginn, List<String> aenderungen) {
        return BenachrichtigungsRegeln.bezeichnung(titel, alterBeginn) + " wurde geändert: "
                + String.join("; ", aenderungen) + ".";
    }

    // ------------------------------------------------------------------ Hilfen

    static String norm(String name) {
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String kuerzen(String text, int laenge) {
        String t = text.strip();
        return t.length() <= laenge ? t : t.substring(0, laenge);
    }

    private static String zeit(Instant zeitpunkt) {
        return ZEIT.format(zeitpunkt.atZone(Uhr.ZONE));
    }

    private static String zeitpunkt(Instant zeitpunkt) {
        ZonedDateTime z = zeitpunkt.atZone(Uhr.ZONE);
        return WOCHENTAGE[z.getDayOfWeek().getValue() - 1] + " " + TAG.format(z) + " " + ZEIT.format(z);
    }
}
