package ch.stvbero.platform.core.testsupport;

import ch.stvbero.platform.core.auth.Public;
import ch.stvbero.platform.core.notification.Deliveries;
import ch.stvbero.platform.core.notification.Delivery;
import ch.stvbero.platform.core.notification.DeliverySimulator;
import ch.stvbero.platform.core.notification.NotificationChannel;
import ch.stvbero.platform.core.notification.Notifications;
import ch.stvbero.platform.core.auth.SessionService;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.error.Problem;
import ch.stvbero.platform.core.person.Account;
import ch.stvbero.platform.core.season.Season;
import ch.stvbero.platform.core.swissunihockey.SuSimulator;
import ch.stvbero.platform.core.swissunihockey.SuMatchDto;
import ch.stvbero.platform.core.swissunihockey.SuLeagueDto;
import ch.stvbero.platform.core.swissunihockey.SuMatch;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Inject
    Deliveries deliveries;

    @Inject
    DeliverySimulator deliverySimulator;

    public record ClockInput(@NotNull OffsetDateTime now) {
    }

    public record ClockState(OffsetDateTime now, boolean fixed) {
    }

    public record DeliveryMode(
            @Schema(description = "Channels whose delivery fails until the next change or reset (F-19, criterion 7)")
            List<@NotNull NotificationChannel> failing,
            @Schema(description = "Push endpoints the simulated push service reports as expired (F-19, criterion 7)")
            List<@NotNull String> expiredEndpoints) {
    }

    public record LoginInput(@NotBlank String loginKey) {
    }

    public record SuPreset(@NotNull @Positive Integer suTeamId,
                            @Schema(defaultValue = "false", description = "Simuliert einen Ausfall (SUH-09)")
                            Boolean unavailable,
                            @NotNull List<@Valid @NotNull SuMatchDto> matches,
                            @Schema(description = "League view (F-17): league, group, table and its own matches. "
                                    + "Omitted: the league view of this team ID stays as it is")
                            @Valid SuLeagueDto league) {
    }

    @POST
    @Path("/reset")
    @Operation(operationId = "testReset", summary = "Reset the database to the seed, the clock to default and the simulator")
    @APIResponse(responseCode = "204", description = "Reset")
    public void reset() {
        deliveries.exclusively(() -> {
            seed.reset();
            simulator.reset();
            deliverySimulator.reset();
        });
    }

    @PUT
    @Path("/delivery")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testSetDelivery",
            summary = "Let e-mail or push fail, or report push endpoints as expired (simulated, F-19)")
    @APIResponse(responseCode = "204", description = "Set")
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void setDelivery(@Valid @NotNull DeliveryMode mode) {
        deliverySimulator.set(mode.failing() == null || mode.failing().isEmpty()
                        ? EnumSet.noneOf(NotificationChannel.class) : EnumSet.copyOf(mode.failing()),
                mode.expiredEndpoints() == null ? Set.of() : new HashSet<>(mode.expiredEndpoints()));
    }

    @PUT
    @Path("/swiss-unihockey/matches")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testSetSuMatches",
            summary = "Set the data the simulator delivers for a swiss unihockey team ID (matches for the sync, "
                    + "league view for the league page, outage)")
    @APIResponse(responseCode = "204", description = "Set")
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void setSuMatches(@Valid @NotNull SuPreset preset) {
        List<SuMatch> matches = preset.matches().stream().map(SuMatchDto::toMatch).toList();
        simulator.set(preset.suTeamId(), matches,
                preset.league() == null ? null : preset.league().toLeague(matches),
                Boolean.TRUE.equals(preset.unavailable()));
    }

    @PUT
    @Path("/clock")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testSetClock",
            summary = "Set the backend clock to a fixed point in time. Runs due jobs (reminders, retries of e-mails "
                    + "and pushes) immediately.")
    @APIResponse(responseCode = "204", description = "Set")
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void setClock(@Valid @NotNull ClockInput input) {
        clock.fixAt(input.now().toInstant());
        reminders.check();
        deliveries.processDue();
    }

    @GET
    @Path("/clock")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testGetClock", summary = "Current backend time")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ClockState.class)))
    public ClockState getClock() {
        return new ClockState(clock.nowWithOffset(), clock.isFixed());
    }

    @DELETE
    @Path("/clock")
    @Operation(operationId = "testReleaseClock", summary = "Use the real time again")
    @APIResponse(responseCode = "204", description = "Released")
    public void releaseClock() {
        clock.release();
    }

    @GET
    @Path("/notifications")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testListNotifications",
            summary = "All notifications of all persons since the last reset, including the seed, each with its "
                    + "e-mails and pushes (F-19). Delivers what is due first.")
    @APIResponse(responseCode = "200", description = "Chronologisch",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = TestNotification.class)))
    public List<TestNotification> notifications() {
        deliveries.processDue();
        return listNotifications();
    }

    private List<TestNotification> listNotifications() {
        Map<UUID, List<Delivery>> byNotification = Delivery.<Delivery>find("order by createdAt, sequenceNumber")
                .list().stream().collect(Collectors.groupingBy(d -> d.notification.id));
        return notifications.all().stream()
                .map(n -> TestNotification.of(n, byNotification.getOrDefault(n.id, List.of()))).toList();
    }

    @DELETE
    @Path("/current-season")
    @Transactional
    @Operation(operationId = "testRemoveCurrentSeason",
            summary = "Mark no season as current any more (state «no current season»). Reset restores the seed.")
    @APIResponse(responseCode = "204", description = "Removed")
    public void removeCurrentSeason() {
        Season.update("current = false where current = true");
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(operationId = "testLogin", summary = "Log in as a seed person without a password. Sets the session cookie.")
    @APIResponse(responseCode = "204", description = "Angemeldet")
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
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
    @Operation(operationId = "testListPersons", summary = "Persons with their key for the test mode login (/dev-login)")
    @APIResponse(responseCode = "200", description = "Nach Nachname, dann Vorname",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(type = SchemaType.ARRAY, implementation = TestPerson.class)))
    public List<TestPerson> persons() {
        return Account.<Account>find("loginKey is not null order by person.lastName, person.firstName").list().stream()
                .map(a -> new TestPerson(a.loginKey, a.person.id, a.person.firstName, a.person.lastName, a.person.admin))
                .toList();
    }
}
