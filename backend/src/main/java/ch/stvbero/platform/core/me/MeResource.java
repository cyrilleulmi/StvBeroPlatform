package ch.stvbero.platform.core.me;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.auth.Public;
import ch.stvbero.platform.core.auth.SessionService;
import ch.stvbero.platform.core.error.Problem;
import ch.stvbero.platform.core.person.Person;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.season.SeasonDto;
import ch.stvbero.platform.core.team.TeamService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import ch.stvbero.platform.core.clock.BackendClock;

@Path("/api")
@Tag(name = "Me")
public class MeResource {

    @Inject
    AuthContext auth;

    @Inject
    SessionService sessions;

    @Inject
    TeamService teams;

    @Inject
    BackendClock clock;

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getMe", summary = "Angemeldete Person mit Teamzugehörigkeiten der aktuellen Saison")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = MeDto.class)))
    @APIResponse(responseCode = "401", description = "Nicht angemeldet",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Widerspricht dem aktuellen Zustand",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public MeDto get() {
        CurrentUser user = auth.person();
        Person person = Person.findById(user.personId());
        Season season = Season.currentOrFail();
        return new MeDto(person.id, person.firstName, person.lastName, person.email, person.admin,
                SeasonDto.of(season, season), teams.mine(person.id, season), clock.nowWithOffset());
    }

    @POST
    @Path("/logout")
    @Public
    @Operation(operationId = "logout", summary = "Sitzung beenden und Sitzungs-Cookie löschen")
    @APIResponse(responseCode = "204", description = "Abgemeldet")
    public Response logout() {
        auth.token().ifPresent(sessions::end);
        return Response.noContent().cookie(sessions.deletionCookie()).build();
    }
}
