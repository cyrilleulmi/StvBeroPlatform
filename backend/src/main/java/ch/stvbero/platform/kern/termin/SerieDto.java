package ch.stvbero.platform.kern.termin;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Serie mit ihren Ausnahmen (TER-05, TER-07). */
@Schema(name = "Serie")
public record SerieDto(UUID id, SerienTyp typ, String titel, List<UUID> teamIds, Wochentag wochentag,
                       String beginnZeit, String endeZeit, LocalDate gueltigVon, LocalDate gueltigBis, OrtDto ort,
                       int antwortfristStunden, List<AusnahmeDto> ausnahmen) {

    public static SerieDto von(Serie s) {
        return new SerieDto(s.id, s.typ, s.titel, s.teams.stream().map(t -> t.id).sorted().toList(), s.wochentag,
                TerminRegeln.zeit(s.beginnZeit), TerminRegeln.zeit(s.endeZeit), s.gueltigVon, s.gueltigBis,
                s.ort == null ? null : s.ort.dto(), s.antwortfristStunden,
                s.ausnahmen.stream().sorted(java.util.Comparator.comparing((Ausnahme a) -> a.von).thenComparing(a -> a.bis))
                        .map(a -> new AusnahmeDto(a.von, a.bis, a.grund)).toList());
    }
}
