package ch.stvbero.platform.core.line;

import java.util.UUID;

/** Gruppe, der eine Spielerin angehören soll. {@code null} = ohne Linie (LIN-06). */
public record AssignmentInput(UUID groupId) {
}
