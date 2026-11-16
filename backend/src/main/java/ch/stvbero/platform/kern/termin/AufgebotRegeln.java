package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.team.Funktion;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Fachregeln zum Aufgebot eines Spieltags (AUF). Ohne Datenbank, damit sie als Unit-Test prüfbar sind. */
public final class AufgebotRegeln {

    public static final String NUR_SPIELTAG = "Nur ein Spieltag hat ein Aufgebot.";
    public static final String NUR_SPIELERINNEN = "Aufbieten lassen sich nur Spielerinnen des Teams. Coaches gehören nicht zum Aufgebot.";
    public static final String SCHON_VEROEFFENTLICHT = "Das Aufgebot ist schon veröffentlicht.";
    public static final String LEER = "Wähle zuerst mindestens eine Spielerin aus.";

    private AufgebotRegeln() {
    }

    /** Ein Aufgebot gibt es nur bei Spieltagen (AUF-01, AUF-02). */
    public static void pruefeSpieltag(TerminTyp typ) {
        if (typ != TerminTyp.SPIELTAG) {
            throw FachFehler.konflikt("Kein Spieltag", NUR_SPIELTAG, "AUF-02");
        }
    }

    /** Aufgebot ansehen, erstellen und veröffentlichen: Coaches des Teams und Admins (RR-03, Rechte-Tabelle). */
    public static boolean darfBearbeiten(boolean admin, Collection<Funktion> funktionenImTeam) {
        return admin || funktionenImTeam.contains(Funktion.COACH);
    }

    public static void pruefeDarfBearbeiten(boolean admin, Collection<Funktion> funktionenImTeam) {
        if (!darfBearbeiten(admin, funktionenImTeam)) {
            throw FachFehler.keineBerechtigung();
        }
    }

    /**
     * Aufbieten lassen sich alle Spielerinnen des Teams, unabhängig von ihrer Antwort (AUF-22), aber keine
     * Coaches (AUF-28) und niemand ausserhalb des Teams: 422.
     */
    public static void pruefeAufbietbar(Collection<UUID> auswahl, Set<UUID> spielerinnen) {
        if (!spielerinnen.containsAll(auswahl)) {
            throw FachFehler.regelverletzung("personIds", "Nicht aufbietbar", NUR_SPIELERINNEN, "AUF-28");
        }
    }

    /** Veröffentlicht wird ein Entwurf mit mindestens einer Spielerin, und nur einmal (AUF-23): sonst 409. */
    public static void pruefeVeroeffentlichbar(AufgebotStatus status) {
        switch (status) {
            case VEROEFFENTLICHT -> throw FachFehler.konflikt("Schon veröffentlicht", SCHON_VEROEFFENTLICHT, "AUF-23");
            case KEINES -> throw FachFehler.konflikt("Aufgebot leer", LEER, "AUF-23");
            case ENTWURF -> {
                // ok
            }
        }
    }

    /** KEINES ohne Auswahl, ENTWURF bis zur Veröffentlichung, danach VEROEFFENTLICHT (zustaende.md). */
    public static AufgebotStatus status(boolean veroeffentlicht, int anzahlAufgeboten) {
        if (veroeffentlicht) {
            return AufgebotStatus.VEROEFFENTLICHT;
        }
        return anzahlAufgeboten > 0 ? AufgebotStatus.ENTWURF : AufgebotStatus.KEINES;
    }

    /**
     * Was jemand vom Stand des Aufgebots sieht: Coaches und Admins den echten Stand, alle anderen erst nach
     * der Veröffentlichung etwas (AUF-23, Annahme: auch dass es einen Entwurf gibt, bleibt verborgen).
     */
    public static AufgebotStatus sichtbarerStatus(AufgebotStatus status, boolean coachOderAdmin) {
        if (coachOderAdmin || status == AufgebotStatus.VEROEFFENTLICHT) {
            return status;
        }
        return AufgebotStatus.KEINES;
    }

    /** Sicht einer Spielerin (AUF-24): vor der Veröffentlichung «noch kein Aufgebot». */
    public static MeinAufgebot meinAufgebot(AufgebotStatus status, boolean aufgeboten) {
        if (status != AufgebotStatus.VEROEFFENTLICHT) {
            return MeinAufgebot.NOCH_KEIN_AUFGEBOT;
        }
        return aufgeboten ? MeinAufgebot.AUFGEBOTEN : MeinAufgebot.NICHT_AUFGEBOTEN;
    }
}
