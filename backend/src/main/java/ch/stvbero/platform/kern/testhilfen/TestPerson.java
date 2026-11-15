package ch.stvbero.platform.kern.testhilfen;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "TestPerson")
public record TestPerson(String schluessel, UUID id, String vorname, String nachname, boolean admin) {
}
