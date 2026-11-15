package ch.stvbero.platform.kern.linie;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.person.PersonKurz;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.team.GruppenArt;
import ch.stvbero.platform.kern.team.TeamDetailDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Fachregeln zu Linien und Goalies (LIN). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class LinienRegeln {

    /**
     * Höchstens so viele Gruppen pro Spielerin und Team (LIN-02). Die Begrenzung ist eine Regel, keine
     * feste Struktur (LIN-04): Für «mehrere Linien pro Spielerin» wird nur diese Zahl (und der Index
     * {@code gruppenzuweisung_eine_gruppe}) angepasst.
     */
    public static final int HOECHSTENS_GRUPPEN_PRO_TEAM = 1;

    /** Eine Gruppe ohne Datenbank. */
    public record GruppeInfo(UUID id, GruppenArt art, String name, Integer reihenfolge) {
    }

    /** Anzeigereihenfolge: Linien nach Reihenfolge, dann Goalies (LIN-09, Abschnitt «Anzeige-Reihenfolge»). */
    public static final Comparator<GruppeInfo> ANZEIGE = Comparator
            .comparing((GruppeInfo g) -> g.art() == GruppenArt.GOALIES ? 1 : 0)
            .thenComparing(g -> g.reihenfolge() == null ? Integer.MAX_VALUE : g.reihenfolge())
            .thenComparing(GruppeInfo::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(GruppeInfo::id);

    private LinienRegeln() {
    }

    /** Linien verwalten und Spielerinnen zuweisen: Coach des Teams oder Admin (LIN-07, RR-03, RR-07). */
    public static void pruefeDarfBearbeiten(boolean admin, Funktion funktionImTeam) {
        if (!admin && funktionImTeam != Funktion.COACH) {
            throw FachFehler.keineBerechtigung();
        }
    }

    /** Einteilung ansehen: Mitglieder des Teams (jede Funktion) und Admins. */
    public static void pruefeDarfLesen(boolean admin, Funktion funktionImTeam) {
        if (!admin && funktionImTeam == null) {
            throw FachFehler.keineBerechtigung();
        }
    }

    /** Nur Spielerinnen des Teams können einer Linie oder den Goalies zugewiesen werden (LIN-10). */
    public static void pruefeZuweisbar(PersonKurz person, Funktion funktionImTeam) {
        if (funktionImTeam != Funktion.SPIELERIN) {
            throw FachFehler.konflikt("Keine Spielerin",
                    person.vorname() + " " + person.nachname()
                            + " ist in diesem Team keine Spielerin und kann keiner Linie zugewiesen werden.", "LIN-10");
        }
    }

    /** Goalies gibt es pro Team genau einmal: nicht löschen, nicht umbenennen, nicht verschieben (LIN-05). */
    public static void pruefeLinie(GruppeInfo gruppe, String aktion) {
        if (gruppe.art() == GruppenArt.GOALIES) {
            throw FachFehler.konflikt("Goalies bleiben",
                    "Die Goalies gehören fest zu jedem Team und lassen sich nicht " + aktion + ".", "LIN-05");
        }
    }

    /** Namen sind pro Team eindeutig, auch gegenüber «Goalies» (Annahme zu LIN-09). */
    public static void pruefeName(Collection<GruppeInfo> gruppen, String name, UUID ausser) {
        String n = name.strip();
        boolean vergeben = gruppen.stream()
                .anyMatch(g -> !g.id().equals(ausser) && g.name().strip().equalsIgnoreCase(n));
        if (vergeben) {
            throw FachFehler.konflikt("Name vergeben", "In diesem Team gibt es schon eine Gruppe «" + n + "».", "LIN-09");
        }
    }

    /**
     * Neue Zuweisungen einer Spielerin in einem Team. {@code ziel = null} heisst «ohne Linie».
     * Sind es mehr als {@link #HOECHSTENS_GRUPPEN_PRO_TEAM}, fallen die ältesten weg: Bei einer Gruppe
     * pro Team ersetzt die neue Zuweisung die bisherige (LIN-02).
     */
    public static Set<UUID> zuweisen(List<UUID> bisher, UUID ziel) {
        if (ziel == null) {
            return Set.of();
        }
        List<UUID> neu = new ArrayList<>(bisher);
        neu.remove(ziel);
        neu.add(ziel);
        while (neu.size() > HOECHSTENS_GRUPPEN_PRO_TEAM) {
            neu.remove(0);
        }
        return new LinkedHashSet<>(neu);
    }

    /**
     * Reihenfolge nach dem Verschieben einer Linie an Position {@code ziel} (1 = zuoberst). Positionen
     * ausserhalb werden auf den gültigen Bereich begrenzt. Ergebnis: Linien-IDs, Position = Index + 1.
     */
    public static List<UUID> verschieben(List<UUID> linienInReihenfolge, UUID id, int ziel) {
        List<UUID> neu = new ArrayList<>(linienInReihenfolge);
        if (!neu.remove(id)) {
            throw new IllegalArgumentException("Linie nicht in der Liste: " + id);
        }
        int index = Math.max(0, Math.min(ziel - 1, neu.size()));
        neu.add(index, id);
        return neu;
    }

    /**
     * Einteilung eines Teams für die Anzeige: alle Gruppen (auch leere) in Anzeigereihenfolge, darin die
     * Spielerinnen nach Nachname, dann Vorname. Spielerinnen ohne Gruppe stehen unter «Ohne Linie» (LIN-06).
     *
     * @param zuweisungen Person-ID → Gruppen-IDs dieser Person im Team
     */
    public static TeamDetailDto.Gruppeneinteilung einteilen(Collection<GruppeInfo> gruppen,
                                                            Collection<PersonKurz> spielerinnen,
                                                            Map<UUID, Set<UUID>> zuweisungen) {
        List<GruppeInfo> sortiert = gruppen.stream().sorted(ANZEIGE).toList();
        List<TeamDetailDto.GruppeMitSpielerinnen> ergebnis = new ArrayList<>();
        for (GruppeInfo g : sortiert) {
            List<PersonKurz> darin = spielerinnen.stream()
                    .filter(p -> zuweisungen.getOrDefault(p.id(), Set.of()).contains(g.id()))
                    .sorted(PersonKurz.REIHENFOLGE).toList();
            ergebnis.add(new TeamDetailDto.GruppeMitSpielerinnen(g.id(), g.art(), g.name(), g.reihenfolge(), darin));
        }
        Set<UUID> gruppenIds = sortiert.stream().map(GruppeInfo::id).collect(java.util.stream.Collectors.toSet());
        List<PersonKurz> ohneLinie = spielerinnen.stream()
                .filter(p -> zuweisungen.getOrDefault(p.id(), Set.of()).stream().noneMatch(gruppenIds::contains))
                .sorted(PersonKurz.REIHENFOLGE).toList();
        return new TeamDetailDto.Gruppeneinteilung(ergebnis, ohneLinie);
    }
}
