package ch.stvbero.platform.core.person;

import ch.stvbero.platform.core.team.TeamMembershipDto;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "PersonDetail")
public record PersonDetailDto(UUID id, String firstName, String lastName, String email, boolean admin,
                              Gender gender, Integer birthYear,
                              List<TeamMembershipDto> memberships) {
}
