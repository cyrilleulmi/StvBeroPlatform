package ch.stvbero.platform.core.testsupport;

import ch.stvbero.platform.core.auth.Public;
import ch.stvbero.platform.core.notification.NotificationDto;
import ch.stvbero.platform.core.notification.Notifications;
import ch.stvbero.platform.core.auth.SessionService;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.error.Problem;
import ch.stvbero.platform.core.person.Account;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.swissunihockey.SuSimulator;
import ch.stvbero.platform.core.swissunihockey.SuMatchDto;
import ch.stvbero.platform.core.event.Reminders;
import ch.stvbero.platform.core.clock.BackendClock;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Test support from docs/testability.md. Exists only in the profiles dev and test: in the prod profile
 * {@code stv.test-support=false}, so the resource is not registered at all (404).
 */
@Path("/api/test")
@Tag(name = "Test")
@Public
@IfBuildProperty(name = "stv.test-support", stringValue = "true")
public class TestResource {

    @Inject
    Seed seed;

    @Inject
    BackendClock clock;

    @Inject
    SessionService sessions;

    @Inject
    Reminders reminders;

    @Inject
    Notifications notifications;

    @Inject
    SuSimulator simulator;

    public record ClockInput(@NotNull OffsetDateTime now) {
    }

    public record ClockState(OffsetDateTime now, boolean fixed) {
    }

    public record LoginInput(@NotBlank String loginKey) {
    }

    public record SuPreset(@NotNull @Positive Integer suTeamId,
                            @Schema(defaultValue = "false", description = "Simuliert einen Ausfall (SUH-09)")
                            Boolean unavailable,
                            @NotNull List<@Valid @NotNull SuMatchDto> matches) {
    }

    @POST
    @Path("/reset")
    @Operation(operationId = "testReset", summary = "Datenbank auf den Seed zurücksetzen, Uhr auf Standard, Simulator zurücksetzen")
    @APIResponse(responseCode = "204", description = "Zurückgesetzt")
    public void reset() {
        seed.reset();
        simulator.reset();
    }

    @PUT
    @Path("/swiss-unihockey/matches")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testSetSuMatches",
            summary = "Daten vorgeben, die der Simulator für eine swiss-unihockey-Team-ID liefert")
    @APIResponse(responseCode = "204", description = "Gesetzt")
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void setSuMatches(@Valid @NotNull SuPreset preset) {
        simulator.set(preset.suTeamId(), preset.matches().stream().map(SuMatchDto::toMatch).toList(),
                Boolean.TRUE.equals(preset.unavailable()));
    }

    @PUT
    @Path("/clock")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testSetClock",
            summary = "Backend-Uhr auf einen festen Zeitpunkt setzen. Löst fällige Jobs (Erinnerungen) sofort aus.")
    @APIResponse(responseCode = "204", description = "Gesetzt")
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void setClock(@Valid @NotNull ClockInput input) {
        clock.fixAt(input.now().toInstant());
        reminders.check();
    }

    @GET
    @Path("/clock")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testGetClock", summary = "Aktuelle Backend-Zeit")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ClockState.class)))
    public ClockState getClock() {
        return new ClockState(clock.nowWithOffset(), clock.isFixed());
    }

    @DELETE
    @Path("/clock")
    @Operation(operationId = "testReleaseClock", summary = "Wieder die echte Zeit verwenden")
    @APIResponse(responseCode = "204", description = "Gelöst")
    public void releaseClock() {
        clock.release();
    }

    @GET
    @Path("/notifications")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testListNotifications",
            summary = "Alle Benachrichtigungen aller Personen seit dem letzten Reset, inklusive Seed")
    @APIResponse(responseCode = "200", description = "Chronologisch",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = NotificationDto.class)))
    public List<NotificationDto> notifications() {
        return notifications.all();
    }

    @DELETE
    @Path("/current-season")
    @Transactional
    @Operation(operationId = "testRemoveCurrentSeason",
            summary = "Keine Saison mehr als aktuell markieren (Zustand «keine aktuelle Saison»). Reset stellt den Seed wieder her.")
    @APIResponse(responseCode = "204", description = "Entfernt")
    public void removeCurrentSeason() {
        Season.update("current = false where current = true");
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testLogin", summary = "Ohne Passwort als Seed-Person anmelden. Setzt das Sitzungs-Cookie.")
    @APIResponse(responseCode = "204", description = "Angemeldet")
    @APIResponse(responseCode = "400", description = "Anfrage formal ungültig",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Nicht gefunden",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response login(@Valid @NotNull LoginInput input) {
        Account account = Account.withLoginKey(input.loginKey())
                .orElseThrow(() -> DomainError.notFound("Keine Person mit dem Schlüssel «" + input.loginKey() + "»."));
        String token = sessions.open(account);
        return Response.noContent().cookie(sessions.cookie(token)).build();
    }

    @GET
    @Path("/persons")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testListPersons", summary = "Personen mit Schlüssel für die Testmodus-Anmeldung (/dev-login)")
    @APIResponse(responseCode = "200", description = "Nach Nachname, dann Vorname",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = TestPerson.class)))
    public List<TestPerson> persons() {
        return Account.<Account>find("loginKey is not null order by person.lastName, person.firstName").list().stream()
                .map(a -> new TestPerson(a.loginKey, a.person.id, a.person.firstName, a.person.lastName, a.person.admin))
                .toList();
    }
}
