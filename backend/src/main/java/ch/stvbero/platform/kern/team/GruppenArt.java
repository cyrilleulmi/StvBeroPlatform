package ch.stvbero.platform.kern.team;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Linie oder Goalies. Die Gruppen selbst kommen mit F-02 (LIN). */
@Schema(name = "GruppenArt")
public enum GruppenArt {
    LINIE, GOALIES
}
