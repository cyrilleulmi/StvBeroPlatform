package ch.stvbero.platform.core.me;

import ch.stvbero.platform.core.season.SeasonDto;
import ch.stvbero.platform.core.team.TeamMembershipDto;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Schema(name = "Me")
public record MeDto(UUID id, String firstName, String lastName, String email, boolean admin,
                     SeasonDto season, List<TeamMembershipDto> memberships,
                     OffsetDateTime now) {
}
