package ch.stvbero.platform.kern.team;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Was eine Person in einem Team ist (PTF-05). Erweiterbar (PTF-06): gespeichert als Text. */
@Schema(name = "Funktion")
public enum Funktion {
    SPIELERIN, COACH
}
