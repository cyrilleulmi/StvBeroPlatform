package ch.stvbero.platform.core.line;

import ch.stvbero.platform.core.team.GroupType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Group")
public record GroupDto(UUID id, GroupType type, String name, Integer sortOrder) {

    public static GroupDto of(Group g) {
        return new GroupDto(g.id, g.type, g.name, g.sortOrder);
    }
}
