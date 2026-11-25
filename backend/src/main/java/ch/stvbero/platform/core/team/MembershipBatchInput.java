package ch.stvbero.platform.core.team;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** Several changes of team memberships at once, e.g. a whole year of juniors moving on (PTF-17). */
@Schema(name = "MembershipBatchInput")
public record MembershipBatchInput(
        @Schema(nullable = true, description = "People to put into a team; someone who is in it with another team role gets the new one")
        @Size(max = 500) List<@Valid @NotNull Add> add,
        @Schema(nullable = true, description = "People to take out of a team; someone who is not in it is skipped")
        @Size(max = 500) List<@Valid @NotNull Remove> remove) {

    @Schema(name = "MembershipAdd")
    public record Add(@NotNull UUID teamId, @NotNull UUID personId, @NotNull TeamRole teamRole) {
    }

    @Schema(name = "MembershipRemove")
    public record Remove(@NotNull UUID teamId, @NotNull UUID personId) {
    }
}
