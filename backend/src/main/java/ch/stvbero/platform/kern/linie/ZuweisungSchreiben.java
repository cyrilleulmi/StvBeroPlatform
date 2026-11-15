package ch.stvbero.platform.kern.linie;

import java.util.UUID;

/** Gruppe, der eine Spielerin angehören soll. {@code null} = ohne Linie (LIN-06). */
public record ZuweisungSchreiben(UUID gruppeId) {
}
