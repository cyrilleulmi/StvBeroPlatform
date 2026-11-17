package ch.stvbero.platform.core.auth;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Session")
public record SessionDto(boolean signedIn) {
}
