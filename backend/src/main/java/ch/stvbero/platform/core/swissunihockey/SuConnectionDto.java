package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.team.SuLink;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The links of a team to swiss unihockey in the current season (SUH-02, SUH-11), each with the state of its last
 * sync (SUH-13).
 */
@Schema(name = "SuConnection")
public record SuConnectionDto(UUID teamId, String teamName,
                              @Schema(description = "Coach or admin: may link, unlink and sync") boolean canManage,
                              List<Link> links) {

    /** One link: the team at swiss unihockey and how its last sync went. */
    @Schema(name = "SuLink")
    public record Link(UUID id, int suTeamId,
                       @Schema(nullable = true, description = "Name at swiss unihockey, e.g. «Damen KF 1. Liga»")
                       String name,
                       @Schema(nullable = true, description = "Time of the last sync; `null`: never synced")
                       OffsetDateTime lastSyncAt,
                       @Schema(nullable = true, description = "`false`: the last sync failed (SUH-13)")
                       Boolean lastSyncOk,
                       @Schema(nullable = true, description = "German sentence about the failure, for display")
                       String lastSyncError) {

        static Link of(SuLink link) {
            return new Link(link.id, link.suTeamId, link.suTeamName,
                    link.lastSyncAt == null ? null : link.lastSyncAt.atZone(BackendClock.ZONE).toOffsetDateTime(),
                    link.lastSyncOk, link.lastSyncError);
        }
    }
}
