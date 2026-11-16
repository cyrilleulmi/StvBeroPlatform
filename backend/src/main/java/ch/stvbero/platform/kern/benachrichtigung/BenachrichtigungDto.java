package ch.stvbero.platform.kern.benachrichtigung;

import ch.stvbero.platform.kern.person.PersonKurz;
import ch.stvbero.platform.kern.uhr.Uhr;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Benachrichtigung für die Liste in der App. {@code terminId} führt zum Termin (BEN-12). */
@Schema(name = "Benachrichtigung")
public record BenachrichtigungDto(UUID id, BenachrichtigungArt art, PersonKurz empfaenger, String text, UUID terminId,
                                  OffsetDateTime erstelltAm, boolean gelesen) {

    static BenachrichtigungDto von(Benachrichtigung b) {
        return new BenachrichtigungDto(b.id, b.art, PersonKurz.von(b.empfaenger), b.text, b.terminId,
                OffsetDateTime.ofInstant(b.erstelltAm, Uhr.ZONE), b.gelesen());
    }
}
