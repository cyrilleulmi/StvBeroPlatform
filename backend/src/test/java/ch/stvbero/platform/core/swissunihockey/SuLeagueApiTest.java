package ch.stvbero.platform.core.swissunihockey;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-17, criterion 8: the real client reads the league table from fixture files (src/test/resources/swissunihockey)
 * through a local HTTP server, never live. Failures become {@link SuUnavailable} (SUH-09).
 */
class SuLeagueApiTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private HttpServer server;
    private final AtomicReference<String> rankings = new AtomicReference<>("rankings-team.json");
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<URI> request = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/rankings", exchange -> {
            request.set(exchange.getRequestURI());
            respond(exchange, rankings.get(), status.get());
        });
        server.createContext("/api/games", exchange -> respond(exchange, "games-team.json", 200));
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String file, int code)
            throws IOException {
        byte[] content;
        try (InputStream in = SuLeagueApiTest.class.getResourceAsStream("/swissunihockey/" + file)) {
            content = in.readAllBytes();
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, content.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(content);
        }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SuApi api() {
        return new SuApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api"),
                Duration.ofSeconds(5));
    }

    @Test
    void f17LeagueTableIsRead() {
        SuLeague league = api().league(900001, 2026, TODAY);

        assertEquals("/api/rankings", request.get().getPath());
        String query = request.get().getQuery();
        assertTrue(query.contains("team_id=900001") && query.contains("season=2026"), query);

        assertEquals("Herren Kleinfeld 3. Liga", league.league());
        assertEquals("Gruppe 12", league.group());
        assertEquals(3, league.table().size());
        SuStanding own = league.table().get(1);
        assertEquals(new SuStanding(2, 900001, "STV Beromünster I", 6, 4, 1, 1, 31, 22, 9), own);
        assertEquals(900012, league.table().get(2).suTeamId(), "Team ID from the row link");
        assertEquals(3, league.matches().size(), "Matches from the match list, as for the sync");
    }

    @Test
    void f17OvertimeColumnsAndGroupInTitle() {
        rankings.set("rankings-overtime.json");
        SuLeague league = api().league(900001, 2026, TODAY);

        assertEquals("Herren Grossfeld 2. Liga", league.league());
        assertEquals("Gruppe 3", league.group());
        SuStanding row = league.table().get(0);
        assertEquals(6, row.wins(), "SnV counts as a win");
        assertEquals(0, row.draws());
        assertEquals(2, row.losses(), "NnV counts as a loss");
        assertEquals(50, row.goalsFor());
        assertEquals(30, row.goalsAgainst());
        assertEquals(18, row.points());
        assertNull(row.suTeamId());
    }

    @Test
    void suh09UnexpectedTable() {
        rankings.set("rankings-unerwartet.json");
        SuUnavailable error = assertThrows(SuUnavailable.class, () -> api().league(900001, 2026, TODAY));
        assertTrue(error.getMessage().contains("Unexpected league table"), error.getMessage());

        rankings.set("games-unerwartet.json");
        assertThrows(SuUnavailable.class, () -> api().league(900001, 2026, TODAY));
    }

    @Test
    void suh09HttpError() {
        status.set(503);
        SuUnavailable error = assertThrows(SuUnavailable.class, () -> api().league(900001, 2026, TODAY));
        assertTrue(error.getMessage().contains("HTTP 503"), error.getMessage());
    }
}
