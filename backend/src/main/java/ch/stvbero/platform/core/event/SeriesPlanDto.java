package ch.stvbero.platform.core.event;

import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Reworking the series of a team (TER-18 to TER-20): overview, what a change would do, ending a series. */
public final class SeriesPlanDto {

    private SeriesPlanDto() {
    }

    /** A series of a team with its upcoming events (TER-18). */
    @Schema(name = "SeriesOverview")
    public record Overview(
            SeriesDto series,
            @Schema(description = "Upcoming events of the series that are not cancelled") int upcoming,
            @Schema(description = "Start of the next upcoming event; missing without one") OffsetDateTime next,
            @Schema(description = "The caller may change the series (coach of a team of the series, admin)") boolean canEdit) {
    }

    /**
     * What a change would do to the upcoming events, without changing anything (TER-19). Past events never
     * change.
     */
    @Schema(name = "SeriesPreview")
    public record Preview(
            @Schema(description = "Upcoming events that take over the new details; responses stay") int adjusted,
            @Schema(description = "New events") int created,
            @Schema(description = "Upcoming events that no longer fit and are removed (nobody has responded)") int removed,
            @Schema(description = "Upcoming events that no longer fit and are cancelled, because someone has responded; everyone is notified") int cancelled,
            @Schema(description = "Upcoming events changed by hand or cancelled before: they stay as they are") int keptAsTheyAre,
            @Schema(description = "First day that changes; missing if nothing changes") LocalDate from) {
    }

    /** Request: end a series from a day on (TER-20). */
    @Schema(name = "SeriesEndInput")
    public record EndInput(
            @NotNull(message = "Bitte das Datum angeben, ab dem es keine Termine mehr gibt.")
            @Schema(description = "From this day on the series has no events any more") LocalDate from) {
    }
}
