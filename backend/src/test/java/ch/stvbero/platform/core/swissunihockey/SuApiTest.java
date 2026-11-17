package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.clock.BackendClock;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-07, criterion 2: the real client against recorded responses (src/test/resources/swissunihockey), through a
 * local HTTP server instead of swiss unihockey. Failures become {@link SuUnavailable} (SUH-09).
 */
class SuApiTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private HttpServer server;
    private final AtomicReference<String> file = new AtomicReference<>("games-team.json");
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<URI> request = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/games", exchange -> {
            request.set(exchange.getRequestURI());
            byte[] content;
            try (InputStream in = SuApiTest.class.getResourceAsStream("/swissunihockey/" + file.get())) {
                content = in.readAllBytes();
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), content.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(content);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SuApi api() {
        return new SuApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"),
                Duration.ofSeconds(5));
    }

    @Test
    void suh01MatchListOfATeamIsRead() {
        List<SuMatch> matches = api().matches(900001, 2026, TODAY);

        assertEquals("/api/games", request.get().getPath());
        String query = request.get().getQuery();
        assertTrue(query.contains("mode=team") && query.contains("team_id=900001") && query.contains("season=2026"),
                query);

        assertEquals(3, matches.size(), "Intermediate row without a match is skipped");
        SuMatch first = matches.get(0);
        assertEquals(5000001, first.suMatchId());
        assertEquals(LocalDateTime.of(2026, 11, 22, 9, 30).atZone(BackendClock.ZONE).toInstant(), first.startTime());
        assertEquals("STV Beromünster I", first.homeTeam());
        assertEquals("UHC Musterdorf", first.awayTeam());
        assertEquals("Sporthalle Seefeld", first.hall());
        assertEquals("Musterdorf", first.location());
        assertEquals(47.2141, first.latitude());
        assertEquals(8.1662, first.longitude());
        assertNull(first.result(), "Empty result");

        SuMatch third = matches.get(2);
        assertEquals(5000003, third.suMatchId(), "Match ID also as text");
        assertEquals("3:5", third.result());
        assertNull(third.latitude());
    }

    @Test
    void relativeDatesAreResolved() {
        file.set("games-relativ.json");
        List<SuMatch> matches = api().matches(900001, 2026, TODAY);

        assertEquals(LocalDateTime.of(2026, 11, 16, 19, 15).atZone(BackendClock.ZONE).toInstant(), matches.get(0).startTime());
        assertEquals("2:2", matches.get(0).result(), "Suffix «n.V.» is dropped");
        assertEquals(LocalDateTime.of(2026, 11, 17, 10, 0).atZone(BackendClock.ZONE).toInstant(), matches.get(1).startTime());
        assertNull(matches.get(1).result(), "«-» is not a result");
    }

    @Test
    void suh09UnexpectedResponse() {
        file.set("games-unerwartet.json");
        SuUnavailable error = assertThrows(SuUnavailable.class, () -> api().matches(900001, 2026, TODAY));
        assertTrue(error.getMessage().contains("Unexpected response"), error.getMessage());
    }

    @Test
    void suh09HttpError() {
        status.set(401);
        SuUnavailable error = assertThrows(SuUnavailable.class, () -> api().matches(900001, 2026, TODAY));
        assertTrue(error.getMessage().contains("HTTP 401"), error.getMessage());
    }

    @Test
    void suh09NotReachable() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        SuApi unreachable = new SuApi(URI.create("http://127.0.0.1:" + freePort + "/api"), Duration.ofSeconds(2));
        assertThrows(SuUnavailable.class, () -> unreachable.matches(900001, 2026, TODAY));
    }
}
