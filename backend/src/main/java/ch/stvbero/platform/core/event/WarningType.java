package ch.stvbero.platform.core.event;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Kind of warning: too few goalies, too few field players, a line with too few players (F-16); matches that
 * swiss unihockey changed and nobody confirmed yet, or no longer delivers (SUH-12).
 */
@Schema(name = "WarningType")
public enum WarningType {
    GOALIES, FIELD_PLAYERS, LINE, MATCH_CHANGED, MATCH_MISSING
}
