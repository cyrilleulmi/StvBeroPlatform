package ch.stvbero.platform.driver;

import com.sun.net.httpserver.HttpExchange;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DRV-15: routes from OpenStreetMap without a key, against answers recorded from Nominatim and OSRM on
 * 21.11.2026 (src/test/resources/osm), through a local HTTP server. No route becomes empty, every failure
 * {@link RoutingUnavailable} (DRV-07).
 */
class OsmApiTest {

    private static final Place HOME = new Place("Oberstufenschulhaus Beromünster", "Oberstufenschulhaus Beromünster",
            null, null);
    private static final Place HOME_WITH_COORDINATES = new Place("Oberstufenschulhaus", "Oberstufenschulhaus",
            47.206668, 8.188356);
    private static final Place VENUE = new Place("AL arena", "AL arena, Gossau ZH", 47.299515, 8.768488);

    private HttpServer server;
    private final AtomicReference<String> routeFile = new AtomicReference<>("route.json");
    private final AtomicReference<Integer> routeStatus = new AtomicReference<>(200);
    private final AtomicReference<Integer> geocodeStatus = new AtomicReference<>(200);
    private final List<URI> requests = new ArrayList<>();
    private final List<String> userAgents = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Like Nominatim on 21.11.2026: the hall is unknown, the town is found
        server.createContext("/search", exchange -> answer(exchange,
                exchange.getRequestURI().getQuery().startsWith("q=Beromünster&")
                        ? "geocode-beromuenster.json" : "geocode-empty.json", geocodeStatus.get()));
        server.createContext("/route/v1/driving", exchange -> answer(exchange, routeFile.get(), routeStatus.get()));
        server.start();
    }

    private void answer(HttpExchange exchange, String file, int status) throws IOException {
        synchronized (requests) {
            requests.add(exchange.getRequestURI());
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
        }
        byte[] content;
        try (InputStream in = OsmApiTest.class.getResourceAsStream("/osm/" + file)) {
            content = in.readAllBytes();
        }
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(content);
        }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OsmApi api() {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        return new OsmApi(base, base, "club-platform", Duration.ofSeconds(5));
    }

    @Test
    void bothPlacesWithCoordinatesAskOnlyTheRouter() {
        Optional<Routing.Result> route = api().route(HOME_WITH_COORDINATES, VENUE);

        // Recorded: Beromünster to Gossau ZH, 89.2 km, 79 minutes
        assertEquals(Optional.of(new Routing.Result(89249, 4740)), route);
        assertEquals(1, requests.size());
        assertEquals("/route/v1/driving/8.188356,47.206668;8.768488,47.299515", requests.get(0).getPath());
        assertEquals("overview=false", requests.get(0).getQuery());
        assertEquals(List.of("club-platform"), userAgents, "the services ask for an identifying user agent");
    }

    @Test
    void placeWithoutCoordinatesIsSearchedAndFallsBackToTheTown() {
        Optional<Routing.Result> route = api().route(HOME, VENUE);

        assertTrue(route.isPresent());
        // The hall is unknown to Nominatim; the search without the first word finds the town
        assertEquals(3, requests.size());
        assertTrue(requests.get(0).getQuery().startsWith("q=Oberstufenschulhaus Beromünster&"));
        assertTrue(requests.get(0).getQuery().contains("countrycodes=ch"));
        assertTrue(requests.get(1).getQuery().startsWith("q=Beromünster&"));
        // Start: the town as Nominatim delivers it (lon,lat)
        assertEquals("/route/v1/driving/8.192439,47.207637;8.768488,47.299515", requests.get(2).getPath());
    }

    @Test
    void searchesGetShorterButNeverASingleShortWord() {
        assertEquals(List.of("Parkplatz Sporthalle Nord", "Sporthalle Nord"),
                OsmApi.searches("Parkplatz Sporthalle Nord"), "«Nord» alone would find some other place");
        assertEquals(List.of("Oberstufenschulhaus Beromünster", "Beromünster"),
                OsmApi.searches(" Oberstufenschulhaus Beromünster "));
        assertEquals(List.of("Bahnhof"), OsmApi.searches("Bahnhof"));
        assertEquals(List.of("Parkplatz beim Schulhaus, Musterdorf", "beim Schulhaus Musterdorf",
                "Schulhaus Musterdorf", "Musterdorf"), OsmApi.searches("Parkplatz beim Schulhaus, Musterdorf"));
    }

    @Test
    void placeNotFoundGivesNoRoute() {
        Place unknown = new Place("Halle 9", "Halle 9", null, null);

        assertEquals(Optional.empty(), api().route(unknown, VENUE));
        assertEquals(1, requests.size(), "no route request without coordinates");
    }

    @Test
    void routerWithoutRouteGivesNoRoute() {
        routeFile.set("route-none.json");
        assertEquals(Optional.empty(), api().route(HOME_WITH_COORDINATES, VENUE));

        // OSRM answers 400 when no road is near a point
        routeStatus.set(400);
        assertEquals(Optional.empty(), api().route(HOME_WITH_COORDINATES, VENUE));
    }

    @Test
    void failuresCountAsUnavailable() {
        routeStatus.set(503);
        assertThrows(RoutingUnavailable.class, () -> api().route(HOME_WITH_COORDINATES, VENUE));
        routeStatus.set(200);
        geocodeStatus.set(429);
        assertThrows(RoutingUnavailable.class, () -> api().route(HOME, VENUE));
        server.stop(0);
        assertThrows(RoutingUnavailable.class, () -> api().route(HOME_WITH_COORDINATES, VENUE));
    }
}
