package ch.stvbero.platform.core.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/**
 * A warning of an event or a call-up for one team (F-16): {@code count} players are attending (or called up),
 * fewer than the team's {@code minimum}. For {@link WarningType#LINE} also the line. The frontend builds the text
 * from it («Kein Goalie angemeldet», «Nur 4 Feldspielerinnen angemeldet», «Linie 2 nur zu 1»).
 */
@Schema(name = "Warning")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WarningDto(WarningType type, OverviewDto.TeamName team, UUID groupId, String groupName, int count,
                         int minimum) {
}
