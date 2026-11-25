package ch.stvbero.platform.notification;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/** The own channels for notifications (F-19, BEN-10): e-mail and push per device. */
@Path("/api")
@Tag(name = "Notifications")
public class NotificationSettingsResource {

    @Inject
    AuthContext auth;

    @Inject
    NotificationSettingsService settings;

    @GET
    @Path("/me/notification-settings")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getMyNotificationSettings",
            summary = "Own channels: e-mail on or off, devices with push (F-19)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = NotificationSettingsDto.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public NotificationSettingsDto mine() {
        return settings.mine(auth.person().personId());
    }

    @PUT
    @Path("/me/notification-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "updateMyNotificationSettings", summary = "Switch e-mails on or off (F-19)")
    @APIResponse(responseCode = "200", description = "Changed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = NotificationSettingsDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "422", description = "No e-mail address (BEN-10)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public NotificationSettingsDto change(@Valid @NotNull NotificationSettingsInput input) {
        return settings.change(auth.person().personId(), input);
    }

    @POST
    @Path("/me/push-subscriptions")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "registerMyPushSubscription",
            summary = "Push on for this device; the same endpoint again updates it (F-19)")
    @APIResponse(responseCode = "201", description = "Registered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PushDeviceDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid request",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "409", description = "Push is not configured",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public Response register(@Valid @NotNull PushSubscriptionInput input, @HeaderParam("User-Agent") String userAgent) {
        return Response.status(Response.Status.CREATED)
                .entity(settings.register(auth.person().personId(), input, userAgent)).build();
    }

    @DELETE
    @Path("/me/push-subscriptions/{subscriptionId}")
    @Operation(operationId = "removeMyPushSubscription", summary = "Push off for a device (F-19)")
    @APIResponse(responseCode = "204", description = "Removed")
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void remove(@PathParam("subscriptionId") UUID id) {
        settings.remove(auth.person().personId(), id);
    }
}
