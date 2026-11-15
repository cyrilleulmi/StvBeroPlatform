package ch.stvbero.platform.kern.anmeldung;

import ch.stvbero.platform.kern.fehler.FachFehler;
import jakarta.enterprise.context.RequestScoped;

import java.util.Optional;

/** Hält die angemeldete Person der laufenden Anfrage. Wird vom {@link AnmeldeFilter} gesetzt. */
@RequestScoped
public class Anmeldung {

    private Angemeldet angemeldet;
    private String token;

    void setzen(Angemeldet angemeldet, String token) {
        this.angemeldet = angemeldet;
        this.token = token;
    }

    public Optional<Angemeldet> optional() {
        return Optional.ofNullable(angemeldet);
    }

    /** Die angemeldete Person, sonst 401. */
    public Angemeldet person() {
        if (angemeldet == null) {
            throw FachFehler.nichtAngemeldet();
        }
        return angemeldet;
    }

    public Optional<String> token() {
        return Optional.ofNullable(token);
    }
}
