package ch.stvbero.platform.core.season;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "Season")
public record SeasonDto(UUID id, String name, LocalDate startDate, LocalDate endDate, boolean current, boolean archived) {

    /** @param current the current season, needed to tell whether {@code s} is archived (SAI-02) */
    public static SeasonDto of(Season s, Season current) {
        return new SeasonDto(s.id, s.name, s.startDate, s.endDate, s.current,
                SeasonRules.isArchived(s.startDate, s.current, current != null ? current.startDate : null));
    }
}
