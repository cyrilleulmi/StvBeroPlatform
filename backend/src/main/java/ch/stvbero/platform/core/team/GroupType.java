package ch.stvbero.platform.core.team;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Linie oder Goalies. Die Gruppen selbst kommen mit F-02 (LIN). */
@Schema(name = "GroupType")
public enum GroupType {
    LINE, GOALIES
}
