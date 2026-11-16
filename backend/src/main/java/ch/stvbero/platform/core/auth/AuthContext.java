package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.error.DomainError;
import jakarta.enterprise.context.RequestScoped;

import java.util.Optional;

/** Hält die angemeldete Person der laufenden Anfrage. Wird vom {@link AnmeldeFilter} gesetzt. */
@RequestScoped
public class AuthContext {

    private CurrentUser signedIn;
    private String token;

    void set(CurrentUser signedIn, String token) {
        this.signedIn = signedIn;
        this.token = token;
    }

    public Optional<CurrentUser> optional() {
        return Optional.ofNullable(signedIn);
    }

    /** Die angemeldete Person, sonst 401. */
    public CurrentUser person() {
        if (signedIn == null) {
            throw DomainError.notSignedIn();
        }
        return signedIn;
    }

    public Optional<String> token() {
        return Optional.ofNullable(token);
    }
}
