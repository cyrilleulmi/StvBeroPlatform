package ch.stvbero.platform.core.team;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Line or goalies. The groups themselves come with F-02 (LIN). */
@Schema(name = "GroupType")
public enum GroupType {
    LINE, GOALIES
}
