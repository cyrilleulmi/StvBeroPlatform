package ch.stvbero.platform.core.line;

import java.util.UUID;

/** Group a player should belong to. {@code null} = no line (LIN-06). */
public record AssignmentInput(UUID groupId) {
}
