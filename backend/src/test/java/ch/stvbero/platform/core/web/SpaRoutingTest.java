package ch.stvbero.platform.core.web;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which paths get the Angular app (E-91). */
class SpaRoutingTest {

    private static final Pattern ROUTE = Pattern.compile(SpaRouting.BROWSER_ROUTE);

    @Test
    void browserRoutesGetTheApp() {
        assertTrue(ROUTE.matcher("/termine").matches());
        assertTrue(ROUTE.matcher("/teams/00000000-0000-0000-0000-000000000001/linien").matches());
        assertTrue(ROUTE.matcher("/dev-login").matches());
        assertTrue(ROUTE.matcher("/apis").matches(), "only the segment /api is reserved");
        assertTrue(ROUTE.matcher("/quiz").matches(), "only the segment /q is reserved");
    }

    @Test
    void rootApiQuarkusAndFilesAreNotRerouted() {
        assertFalse(ROUTE.matcher("/").matches(), "the root is index.html itself");
        assertFalse(ROUTE.matcher("/api").matches());
        assertFalse(ROUTE.matcher("/api/session").matches());
        assertFalse(ROUTE.matcher("/q/health/ready").matches());
        assertFalse(ROUTE.matcher("/sw.js").matches());
        assertFalse(ROUTE.matcher("/icons/icon.svg").matches());
        assertFalse(ROUTE.matcher("/main-ABC123.js").matches());
    }
}
