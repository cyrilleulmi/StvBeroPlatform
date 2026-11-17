package ch.stvbero.platform.core.person;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Path("/api/persons")
@Tag(name = "Persons")
@Produces(MediaType.APPLICATION_JSON)
public class PersonResource {

    @Inject
    AuthContext auth;

    @Inject
    PersonService persons;

    @GET
    @Operation(operationId = "listPersons", summary = "All persons (admin, coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = PersonDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public List<PersonDto> list(@QueryParam("search") String search) {
        return persons.list(auth.person(), search);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "createPerson", summary = "Create a person (admin, coach; only admins set the admin flag)")
    @APIResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PersonDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response create(@Valid @NotNull PersonInput input) {
        return Response.status(201).entity(persons.create(auth.person(), input)).build();
    }

    @GET
    @Path("/{personId}")
    @Operation(operationId = "getPerson", summary = "Person with all team memberships (admin, coach)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PersonDetailDto.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public PersonDetailDto get(@PathParam("personId") UUID personId) {
        return persons.get(auth.person(), personId);
    }

    @PUT
    @Path("/{personId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updatePerson", summary = "Update a person (admin, coach; only admins change the admin flag)")
    @APIResponse(responseCode = "200", description = "Updated",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = PersonDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Conflicts with the current state",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public PersonDto update(@PathParam("personId") UUID personId, @Valid @NotNull PersonInput input) {
        return persons.update(auth.person(), personId, input);
    }
}
