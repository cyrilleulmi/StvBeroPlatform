package ch.stvbero.platform.core.auth;

import jakarta.ws.rs.core.UriInfo;

/**
 * Extension point of the {@link AuthFilter}: a module widens the rights of the signed-in person for one request.
 * The core knows no events, so the module that owns them implements this (special permissions per event, RR-09).
 */
public interface RequestGrant {

    /** The rights the person has for this request: {@code user} itself, or a copy with more rights. */
    CurrentUser apply(UriInfo uri, CurrentUser user);
}
