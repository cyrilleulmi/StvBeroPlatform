package ch.stvbero.platform.core.team;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** What a person is in a team (PTF-05). Extensible (PTF-06): stored as text. */
@Schema(name = "TeamRole")
public enum TeamRole {
    PLAYER, COACH
}
