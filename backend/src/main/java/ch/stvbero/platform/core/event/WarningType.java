package ch.stvbero.platform.core.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Kind of warning (F-16): too few goalies, too few field players, a line with too few players. */
@Schema(name = "WarningType")
public enum WarningType {
    GOALIES, FIELD_PLAYERS, LINE
}
