package ch.stvbero.platform.driver;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.error.Problem;
import ch.stvbero.platform.core.person.Person;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * Whether a person can drive and how many places her car has (DRV-18). The person herself, coaches (of any team,
 * E-69) and admins; enforced here in the backend (RR-07).
 */
@Path("/api/persons/{personId}/driving")
@Tag(name = "MatchDays")
@Produces(MediaType.APPLICATION_JSON)
public class DrivingResource {

    @Inject
    AuthContext auth;

    @GET
    @Operation(operationId = "getDriving", summary = "Whether a person can drive and the places of her car (DRV-18)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DrivingDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DrivingDto get(@PathParam("personId") UUID personId) {
        Person person = find(auth.person(), personId);
        return new DrivingDto(person.canDrive, person.carSeats);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @Operation(operationId = "setDriving", summary = "Change whether a person can drive and the places of her car (DRV-18)")
    @APIResponse(responseCode = "200", description = "Changed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = DrivingDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public DrivingDto set(@PathParam("personId") UUID personId, @Valid @NotNull DrivingDto input) {
        Person person = find(auth.person(), personId);
        person.canDrive = input.canDrive();
        person.carSeats = input.carSeats();
        return new DrivingDto(person.canDrive, person.carSeats);
    }

    private static Person find(CurrentUser user, UUID personId) {
        if (!user.allTeams() && !user.personId().equals(personId)) {
            throw DomainError.forbidden();
        }
        return Person.<Person>findByIdOptional(personId)
                .orElseThrow(() -> DomainError.notFound("Diese Person gibt es nicht."));
    }
}
