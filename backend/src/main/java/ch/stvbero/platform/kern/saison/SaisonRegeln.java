package ch.stvbero.platform.kern.saison;

import ch.stvbero.platform.kern.fehler.FachFehler;

import java.time.LocalDate;
import java.util.Collection;

/** Fachregeln zu Saisons (SAI). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class SaisonRegeln {

    private SaisonRegeln() {
    }

    /** Eine bestehende Saison für die Prüfungen: Name und Zeitraum. */
    public record Zeitraum(String name, LocalDate von, LocalDate bis) {
    }

    /**
     * Archiv sind alle Saisons, die vor der aktuellen beginnen (SAI-02). Eine neue Saison nach der
     * aktuellen ist kein Archiv: Sie wird vorbereitet und kann angepasst werden (SAI-04).
     */
    public static boolean istArchiv(LocalDate von, boolean aktuell, LocalDate vonDerAktuellen) {
        return !aktuell && vonDerAktuellen != null && von.isBefore(vonDerAktuellen);
    }

    /** Änderungen an Teams und Zugehörigkeiten einer archivierten Saison sind nicht möglich (SAI-02). */
    public static void pruefeBeschreibbar(LocalDate von, boolean aktuell, LocalDate vonDerAktuellen) {
        if (istArchiv(von, aktuell, vonDerAktuellen)) {
            throw FachFehler.konflikt("Saison im Archiv",
                    "Diese Saison ist im Archiv und kann nur angesehen werden.", "SAI-02");
        }
    }

    /** Neue Saison: Zeitraum gültig, Name eindeutig, keine Überschneidung mit einer anderen Saison. */
    public static void pruefeNeu(Zeitraum neu, Collection<Zeitraum> bestehende) {
        if (neu.bis().isBefore(neu.von())) {
            throw FachFehler.ungueltigesFeld("bis", "Das Ende muss nach dem Beginn liegen.");
        }
        for (Zeitraum b : bestehende) {
            if (b.name().equalsIgnoreCase(neu.name().strip())) {
                throw FachFehler.konflikt("Name vergeben",
                        "Es gibt schon eine Saison «" + b.name() + "».", "SAI-01");
            }
        }
        for (Zeitraum b : bestehende) {
            boolean ueberschneidet = !neu.von().isAfter(b.bis()) && !neu.bis().isBefore(b.von());
            if (ueberschneidet) {
                throw FachFehler.konflikt("Zeitraum überschneidet sich",
                        "Der Zeitraum überschneidet sich mit der Saison «" + b.name() + "».", "SAI-01");
            }
        }
    }
}
