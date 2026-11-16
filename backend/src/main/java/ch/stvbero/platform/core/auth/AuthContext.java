package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.error.DomainError;
import jakarta.enterprise.context.RequestScoped;

import java.util.Optional;

/** Holds the signed-in user of the current request. Set by the {@link AuthFilter}. */
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

    /** The signed-in user, otherwise 401. */
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
