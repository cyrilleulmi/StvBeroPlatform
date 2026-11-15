package ch.stvbero.platform.kern.saison;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "Saison")
public record SaisonDto(UUID id, String name, LocalDate von, LocalDate bis, boolean aktuell, boolean archiv) {

    /** @param aktuelle die aktuelle Saison, damit sich «Archiv» bestimmen lässt (SAI-02) */
    public static SaisonDto von(Saison s, Saison aktuelle) {
        return new SaisonDto(s.id, s.name, s.von, s.bis, s.aktuell,
                SaisonRegeln.istArchiv(s.von, s.aktuell, aktuelle != null ? aktuelle.von : null));
    }
}
