package ch.stvbero.platform.core.testsupport;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "TestPerson")
public record TestPerson(String loginKey, UUID id, String firstName, String lastName, boolean admin) {
}
