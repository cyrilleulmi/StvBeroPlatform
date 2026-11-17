package ch.stvbero.platform.core.swissunihockey;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Result of a sync (SUH-03).
 *
 * @param created        newly created matches
 * @param changed        applied changes (time, hall, opponent, home/away, result, delivered again)
 * @param missing        matches that swiss unihockey no longer delivers (marked, not deleted)
 * @param unchanged      matches without changes
 * @param affectedEvents created or changed match days
 */
@Schema(name = "SyncResult")
public record SyncResultDto(int created, int changed, int missing, int unchanged,
                                  List<UUID> affectedEvents) {
}
