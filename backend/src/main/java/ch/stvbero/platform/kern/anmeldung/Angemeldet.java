package ch.stvbero.platform.kern.anmeldung;

import ch.stvbero.platform.kern.fehler.FachFehler;

import java.util.UUID;

/**
 * Wer die aktuelle Anfrage stellt. Admin ist eine Eigenschaft der Person (RR-04); die Rechte in
 * einem Team ergeben sich aus der Funktion dort (RR-01).
 */
public record Angemeldet(UUID kontoId, UUID personId, String vorname, boolean admin) {

    /** Verwaltung von Personen, Teams und Saisons nur für Admins (RR-04, RR-07), sonst 403. */
    public void pruefeAdmin() {
        if (!admin) {
            throw FachFehler.keineBerechtigung();
        }
    }
}
