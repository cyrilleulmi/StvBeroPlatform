package ch.stvbero.platform.core.event;

import ch.stvbero.platform.core.person.PersonSummary;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Special permissions of an event or a series (RR-09 to RR-14).
 *
 * @param grants     people who manage the event like a coach, by name
 * @param canManage  the caller may change them (coaches and admins, RR-11)
 * @param candidates members of the teams who can get one (RR-12); only for {@code canManage}
 */
@Schema(name = "EventGrants")
public record EventGrantsDto(
        @Schema(required = true) List<Grant> grants,
        @Schema(required = true) boolean canManage,
        @Schema(description = "Members of the teams who can get a special permission; only with canManage")
        List<PersonSummary> candidates) {

    /**
     * @param viaSeries the permission comes from the series and applies to all its events (RR-10)
     */
    @Schema(name = "EventGrant")
    public record Grant(@Schema(required = true) PersonSummary person, @Schema(required = true) boolean viaSeries) {
    }
}
