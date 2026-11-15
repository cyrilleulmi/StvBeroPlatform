package ch.stvbero.platform.kern.saison;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "Saison")
public record SaisonDto(UUID id, String name, LocalDate von, LocalDate bis, boolean aktuell) {

    public static SaisonDto von(Saison s) {
        return new SaisonDto(s.id, s.name, s.von, s.bis, s.aktuell);
    }
}
