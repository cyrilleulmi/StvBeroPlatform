package ch.stvbero.platform.core.swissunihockey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The swiss unihockey client against responses recorded from {@code wc.swissunihockey.ch} on 21.11.2026
 * (src/test/resources/swissunihockey/live-*.json, O-21, O-59): the match list of «Junioren B» with two cancelled
 * matches, the league table of «Damen KF 1. Liga» and the teams of the club (SUH-02). Through a local HTTP server.
 */
class SuLiveResponsesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);

    private HttpServer server;
    private final List<URI> requests = new ArrayList<>();
    private final AtomicReference<Integer> status = new AtomicReference<>(200);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/games", exchange -> answer(exchange, "live-games-team.json"));
        server.createContext("/rankings", exchange -> answer(exchange, "live-rankings-team.json"));
        server.createContext("/teams", exchange -> answer(exchange, "live-teams-club.json"));
        server.start();
    }

    private void answer(com.sun.net.httpserver.HttpExchange exchange, String file) throws IOException {
        synchronized (requests) {
            requests.add(exchange.getRequestURI());
        }
        byte[] content;
        try (InputStream in = SuLiveResponsesTest.class.getResourceAsStream("/swissunihockey/" + file)) {
            content = in.readAllBytes();
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status.get(), content.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(content);
        }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Test
    void realMatchListIsReadAndCancelledMatchesAreLeftOut() {
        List<SuMatch> matches = new SuApi(base(), Duration.ofSeconds(5)).matches(428960, 2026, TODAY);

        // 18 rows, two of them «Abgesagt» with hall «???»: they count as no longer delivered (SUH-12)
        assertEquals(16, matches.size());
        assertTrue(matches.stream().noneMatch(match -> match.suMatchId() == 1107397 || match.suMatchId() == 1107400));
        SuMatch first = matches.get(0);
        assertEquals("Sporthalle Waltenschwil", first.hall());
        assertEquals("Waltenschwil", first.location());
        assertNotNull(first.latitude(), "the real interface delivers coordinates instead of a map link");
        assertNotNull(first.longitude());
        assertNull(first.mapsUrl());
        assertTrue(SyncRules.mapsUrl(first).startsWith("https://www.google.com/maps/search/?api=1&query=47."),
                "the Google Maps link is built from the coordinates (SUH-07)");
        // The own team appears in every match
        assertEquals("eintracht beromünster", SyncRules.ownTeam(matches, "Junioren B"));
    }

    @Test
    void realLeagueTableIsReadWithACleanLeagueName() {
        SuLeague league = new SuApi(base(), Duration.ofSeconds(5)).league(428361, 2026, TODAY);

        assertEquals("Damen KF 1. Liga", league.league());
        assertEquals("Gruppe 2", league.group());
        assertEquals(10, league.table().size());
        SuStanding leader = league.table().get(0);
        assertEquals("UHCevi Gossau", leader.team());
        assertEquals(1, leader.rank());
        assertEquals(12, leader.points());
        assertEquals(40, leader.goalsFor());
        assertEquals(18, leader.goalsAgainst());
    }

    @Test
    void leagueNameLosesWhatTheInterfacePutsAroundIt() {
        assertEquals("Damen KF 1. Liga Gruppe 2",
                SuRanking.leagueName("Tabelle Damen KF 1. Liga Gruppe 2 2026/27 per 21.11.2026"));
        assertEquals("Herren Kleinfeld 3. Liga", SuRanking.leagueName("Herren Kleinfeld 3. Liga"));
        assertEquals("Herren KF 3. Liga Gruppe 6", SuRanking.leagueName("Tabelle Herren KF 3. Liga Gruppe 6 2026/27"));
        assertNull(SuRanking.leagueName(null));
    }

    @Test
    void suh02TeamsOfTheClubAreRead() {
        List<SuClubTeam> teams = new SuApi(base(), Duration.ofSeconds(5)).clubTeams(417058, 2026);

        assertEquals(10, teams.size());
        assertEquals(new SuClubTeam(428216, "Herren KF 3. Liga"), teams.get(0));
        assertTrue(teams.contains(new SuClubTeam(428361, "Damen KF 1. Liga")));
        String query = requests.get(0).getQuery();
        assertTrue(query.contains("mode=by_club") && query.contains("club_id=417058") && query.contains("season=2026"),
                query);
    }

    @Test
    void teamListWithoutEntriesCountsAsUnexpected() throws IOException {
        assertThrows(SuUnavailable.class, () -> SuApi.clubTeams(new ObjectMapper().readTree("{\"type\":\"table\"}")));
        status.set(403);
        assertThrows(SuUnavailable.class, () -> new SuApi(base(), Duration.ofSeconds(5)).clubTeams(417058, 2026));
    }

    @Test
    void suh14CachedResponseIsReusedInsteadOfAskingAgain(@TempDir Path cache) {
        SuApi cached = new SuApi(base(), Duration.ofSeconds(5), cache, Duration.ofHours(1));
        assertEquals(10, cached.clubTeams(417058, 2026).size());
        assertEquals(10, cached.clubTeams(417058, 2026).size());
        assertEquals(1, requests.size(), "the second call is answered from the cache");

        // Another question is not answered from the cache of the first one
        cached.clubTeams(417058, 2025);
        assertEquals(2, requests.size());

        // Even when swiss unihockey is down, a fresh entry answers; an expired one does not
        server.stop(0);
        assertEquals(10, cached.clubTeams(417058, 2026).size());
        SuApi expired = new SuApi(base(), Duration.ofSeconds(5), cache, Duration.ZERO);
        assertThrows(SuUnavailable.class, () -> expired.clubTeams(417058, 2026));
    }
}
