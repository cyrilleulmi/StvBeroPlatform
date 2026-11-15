package ch.stvbero.platform.kern.anmeldung;

import ch.stvbero.platform.kern.person.Konto;
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
public class SitzungService {

    public static final String COOKIE = "stv_sitzung";

    private final SecureRandom zufall = new SecureRandom();

    @Inject
    Clock clock;

    @ConfigProperty(name = "stv.sitzung.cookie-secure")
    boolean cookieSecure;

    @Transactional
    public String eroeffnen(Konto konto) {
        byte[] bytes = new byte[32];
        zufall.nextBytes(bytes);
        Sitzung sitzung = new Sitzung();
        sitzung.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sitzung.konto = konto;
        sitzung.erstelltAm = clock.instant();
        sitzung.persist();
        return sitzung.token;
    }

    public Optional<Angemeldet> finden(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Sitzung.<Sitzung>findByIdOptional(token)
                .map(s -> new Angemeldet(s.konto.id, s.konto.person.id, s.konto.person.vorname, s.konto.person.admin));
    }

    @Transactional
    public void beenden(String token) {
        Sitzung.deleteById(token);
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

    public NewCookie cookieLoeschen() {
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
