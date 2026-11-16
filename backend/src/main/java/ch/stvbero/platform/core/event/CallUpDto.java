package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Aufgebot eines Spieltags für die Coach: alle Spielerinnen des Teams, gruppiert wie in der Übersicht, mit
 * Antwortstatus und Auswahl (AUF-21, AUF-22). Coaches stehen nicht darin (AUF-28).
 */
@Schema(name = "CallUp")
public record CallUpDto(UUID eventId, CallUpStatus status, OffsetDateTime publishedAt,
                          int calledUpCount, List<Group> groups) {

    @Schema(name = "CallUpGroup")
    public record Group(UUID groupId, OverviewDto.Type type, String name, List<Entry> entries) {
    }

    @Schema(name = "CallUpEntry")
    public record Entry(PersonSummary person, ResponseDto response, boolean calledUp) {
    }
}
