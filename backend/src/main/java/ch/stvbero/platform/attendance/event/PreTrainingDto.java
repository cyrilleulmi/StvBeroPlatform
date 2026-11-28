package ch.stvbero.platform.attendance.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * What the people of a practice do together before it starts (TER-29), e.g. «Joggen» at 20:00 before the practice at
 * 20:20. It ends when the practice starts. The input is checked by {@link PreTrainingRules} (not by Bean Validation),
 * so that the error names the field {@code preTraining.title} or {@code preTraining.start}.
 */
@Schema(name = "PreTraining")
public record PreTrainingDto(String title, OffsetDateTime start) {
}
