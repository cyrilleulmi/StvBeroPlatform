package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.person.Account;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamRole;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.NewCookie;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/** Sessions via the cookie {@value #COOKIE}. The production login method is still open (O-01). */
@ApplicationScoped
public class SessionService {

    public static final String COOKIE = "stv_session";

    private final SecureRandom random = new SecureRandom();

    @Inject
    Clock clock;

    @ConfigProperty(name = "stv.session.cookie-secure")
    boolean cookieSecure;

    @Transactional
    public String open(Account account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        Session session = new Session();
        session.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.account = account;
        session.createdAt = clock.instant();
        session.persist();
        return session.token;
    }

    public Optional<CurrentUser> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Session.<Session>findByIdOptional(token)
                .map(session -> new CurrentUser(session.account.id, session.account.person.id,
                        session.account.person.firstName, session.account.person.admin,
                        isCoach(session.account.person.id)));
    }

    /** Coach of at least one team in the current season (RR-03, E-69). Without a current season: no. */
    static boolean isCoach(UUID personId) {
        return TeamMembership.count("person.id = ?1 and season.current = true and teamRole = ?2",
                personId, TeamRole.COACH) > 0;
    }

    /**
     * Ends the session, idempotently: if it is already deleted (e.g. a second, simultaneous
     * log out), nothing happens. That is why this is a bulk delete instead of load and remove, which
     * throws an OptimisticLockException for a row deleted in the meantime (follow-up fix #26).
     */
    @Transactional
    public void end(String token) {
        Session.delete("token", token);
    }

    public NewCookie cookie(String token) {
        return new NewCookie.Builder(COOKIE)
                .value(token)
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(NewCookie.SameSite.LAX)
                .build();
    }

    public NewCookie deletionCookie() {
        return new NewCookie.Builder(COOKIE)
                .value("")
                .path("/")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(0)
                .build();
    }
}
