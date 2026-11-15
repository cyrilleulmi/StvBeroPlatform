package ch.stvbero.platform.kern.anmeldung;

import java.util.UUID;

/**
 * Wer die aktuelle Anfrage stellt. Rechte ergeben sich später aus den Funktionen pro Team (RR-01);
 * heute gibt es nur die Eigenschaft Admin (RR-04).
 */
public record Angemeldet(UUID kontoId, UUID personId, String vorname, boolean admin) {
}
