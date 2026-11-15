package ch.stvbero.platform.kern.kachel;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Kachel")
public record Kachel(String id, String titel, String icon, String route, KachelFarbe farbe, String hinweis) {
}
