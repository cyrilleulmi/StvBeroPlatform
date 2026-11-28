package ch.stvbero.platform.attendance.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Pre-training of every practice of a series (TER-30), with a time of day like {@code startTime}. The input is
 * checked by {@link PreTrainingRules}; errors name the field {@code preTraining.title} or
 * {@code preTraining.startTime}.
 */
@Schema(name = "SeriesPreTraining")
public record SeriesPreTrainingDto(String title, String startTime) {
}
