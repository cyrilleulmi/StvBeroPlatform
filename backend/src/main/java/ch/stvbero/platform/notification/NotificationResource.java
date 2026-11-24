package ch.stvbero.platform.notification;

import ch.stvbero.platform.core.auth.AuthContext;
import ch.stvbero.platform.core.error.Problem;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/** The user's own notifications in the app (BEN, E-56). */
@Path("/api/notifications")
@Tag(name = "Notifications")
public class NotificationResource {

    @Inject
    AuthContext auth;

    @Inject
    Notifications notifications;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "listNotifications", summary = "The person's own notifications, newest first (BEN-12)")
    @APIResponse(responseCode = "200", description = "OK",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(type = SchemaType.ARRAY, implementation = NotificationDto.class)))
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public List<NotificationDto> get(@QueryParam("unread") Boolean unread) {
        return notifications.own(auth.person(), Boolean.TRUE.equals(unread));
    }

    @POST
    @Path("/{notificationId}/read")
    @Operation(operationId = "markNotificationRead", summary = "Mark as read")
    @APIResponse(responseCode = "204", description = "Markiert")
    @APIResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "403", description = "Forbidden (RR-07)",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    @APIResponse(responseCode = "404", description = "Not found",
            content = @Content(mediaType = Problem.MEDIA_TYPE, schema = @Schema(implementation = Problem.class)))
    public void read(@PathParam("notificationId") UUID id) {
        notifications.read(auth.person(), id);
    }
}
