package ch.stvbero.platform.driver;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Trips and credited kilometres per person in a season (DRV-09). */
@Schema(name = "DriverSummary")
public record DriverSummaryDto(UUID seasonId, String seasonName,
                               @Schema(description = "Coach or admin: all drivers; otherwise only the caller") boolean everyone,
                               List<Row> rows) {

    @Schema(name = "DriverSummaryRow")
    public record Row(PersonSummary person, int trips, double kilometres,
                      @Schema(description = "Trips without known distance (no kilometres credited)") int tripsWithoutDistance) {
    }
}
