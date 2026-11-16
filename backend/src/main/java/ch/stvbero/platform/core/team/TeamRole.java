package ch.stvbero.platform.core.team;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Was eine Person in einem Team ist (PTF-05). Erweiterbar (PTF-06): gespeichert als Text. */
@Schema(name = "TeamRole")
public enum TeamRole {
    PLAYER, COACH
}
