package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.team.TeamDto;
import ch.stvbero.platform.kern.team.TeamSaison;
import ch.stvbero.platform.kern.team.Teamzugehoerigkeit;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Serie mit ihren Ausnahmen (TER-05, TER-07) und allen Teams mit Namen (TER-11). */
@Schema(name = "Serie")
public record SerieDto(UUID id, SerienTyp typ, String titel, List<UUID> teamIds, List<TeamDto> teams,
                       Wochentag wochentag, String beginnZeit, String endeZeit, LocalDate gueltigVon,
                       LocalDate gueltigBis, OrtDto ort, int antwortfristStunden, List<AusnahmeDto> ausnahmen) {

    public static SerieDto von(Serie s, Angemeldet wer) {
        return new SerieDto(s.id, s.typ, s.titel, s.teams.stream().map(t -> t.id).sorted().toList(), teams(s, wer),
                s.wochentag, TerminRegeln.zeit(s.beginnZeit), TerminRegeln.zeit(s.endeZeit), s.gueltigVon,
                s.gueltigBis, s.ort == null ? null : s.ort.dto(), s.antwortfristStunden,
                s.ausnahmen.stream().sorted(Comparator.comparing((Ausnahme a) -> a.von).thenComparing(a -> a.bis))
                        .map(a -> new AusnahmeDto(a.von, a.bis, a.grund)).toList());
    }

    /** Alle Teams der Serie nach Name, mit dem Namen in der Saison der Serie (SAI-02) und der eigenen Funktion. */
    private static List<TeamDto> teams(Serie s, Angemeldet wer) {
        List<UUID> ids = s.teams.stream().map(t -> t.id).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Funktion> meine = new HashMap<>();
        Teamzugehoerigkeit.<Teamzugehoerigkeit>list("person.id = ?1 and saison.id = ?2 and team.id in ?3",
                wer.personId(), s.saison.id, ids).forEach(z -> meine.putIfAbsent(z.team.id, z.funktion));
        return TeamSaison.<TeamSaison>list("saison.id = ?1 and team.id in ?2", s.saison.id, ids).stream()
                .map(ts -> TeamDto.von(ts, meine.get(ts.team.id)))
                .sorted(Comparator.comparing(TeamDto::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TeamDto::id))
                .toList();
    }
}
