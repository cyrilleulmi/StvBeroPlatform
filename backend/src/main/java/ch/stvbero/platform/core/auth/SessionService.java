package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.person.Account;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.NewCookie;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;

/** Sitzungen über das Cookie {@value #COOKIE}. Das produktive Anmeldeverfahren ist offen (O-01). */
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
                .map(s -> new CurrentUser(s.account.id, s.account.person.id, s.account.person.firstName, s.account.person.admin));
    }

    /**
     * Beendet die Sitzung, idempotent: Ist sie schon gelöscht (z.B. zweite, gleichzeitige
     * Abmeldung), passiert nichts. Darum ein Bulk-Delete statt Laden und Entfernen, das bei
     * einer inzwischen gelöschten Zeile eine OptimisticLockException wirft (Nachbesserung #26).
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
