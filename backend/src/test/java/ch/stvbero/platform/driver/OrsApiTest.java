package ch.stvbero.platform.driver;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-20, criterion 6 (DRV-06): the real OpenRouteService client against recorded answers
 * (src/test/resources/openrouteservice), through a local HTTP server. No route becomes empty, every failure
 * {@link RoutingUnavailable} (DRV-07). The key goes in the header, never into the address.
 */
class OrsApiTest {

    private static final String KEY = "test-key-123";
    private static final Place MEETING_POINT = Places.meetingPoint("Parkplatz Sporthalle Nord");
    private static final Place VENUE_WITH_COORDINATES = new Place("Sporthalle Seefeld", "Sporthalle Seefeld",
            47.193521, 8.354127);

    private HttpServer server;
    private final AtomicReference<String> geocodeFile = new AtomicReference<>("geocode-seefeld.json");
    private final AtomicReference<String> directionsFile = new AtomicReference<>("directions.json");
    private final AtomicReference<Integer> directionsStatus = new AtomicReference<>(200);
    private final AtomicReference<Integer> geocodeStatus = new AtomicReference<>(200);
    private final List<URI> requests = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/geocode/search", exchange -> answer(exchange, geocodeFile.get(), geocodeStatus.get()));
        server.createContext("/v2/directions/driving-car",
                exchange -> answer(exchange, directionsFile.get(), directionsStatus.get()));
        server.start();
    }

    private void answer(com.sun.net.httpserver.HttpExchange exchange, String file, int status) throws IOException {
        synchronized (requests) {
            requests.add(exchange.getRequestURI());
            keys.add(exchange.getRequestHeaders().getFirst("Authorization"));
        }
        byte[] content;
        try (InputStream in = OrsApiTest.class.getResourceAsStream("/openrouteservice/" + file)) {
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

    private OrsApi api(String key) {
        return new OrsApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), key,
                Duration.ofSeconds(5));
    }

    @Test
    void geocodesThePlaceWithoutCoordinatesThenAsksForTheRoute() {
        Optional<Routing.Result> route = api(KEY).route(MEETING_POINT, VENUE_WITH_COORDINATES);

        assertEquals(Optional.of(new Routing.Result(23413, 1561)), route);
        assertEquals(2, requests.size());
        assertEquals("/geocode/search", requests.get(0).getPath());
        assertTrue(requests.get(0).getQuery().contains("text=Parkplatz Sporthalle Nord"));
        assertTrue(requests.get(0).getQuery().contains("boundary.country=CH"));
        // Start: the geocoded meeting point (lon,lat), end: the known coordinates of the venue
        assertEquals("/v2/directions/driving-car", requests.get(1).getPath());
        assertEquals("start=8.354127,47.193521&end=8.354127,47.193521", requests.get(1).getQuery());
        assertEquals(List.of(KEY, KEY), keys);
        requests.forEach(uri -> assertFalse(uri.toString().contains(KEY), "key in the address: " + uri));
    }

    @Test
    void bothPlacesWithCoordinatesNeedNoGeocoding() {
        Place from = new Place("Bahnhof", "Bahnhof", 47.22, 8.17);
        api(KEY).route(from, VENUE_WITH_COORDINATES);

        assertEquals(1, requests.size());
        assertEquals("start=8.170000,47.220000&end=8.354127,47.193521", requests.get(0).getQuery());
    }

    @Test
    void placeNotFoundByTheGeocoderGivesNoRoute() {
        geocodeFile.set("geocode-empty.json");

        assertEquals(Optional.empty(), api(KEY).route(MEETING_POINT, VENUE_WITH_COORDINATES));
        assertEquals(1, requests.size(), "no route request without coordinates");
    }

    @Test
    void noRoutablePointGivesNoRoute() {
        directionsFile.set("directions-not-found.json");
        directionsStatus.set(404);

        assertEquals(Optional.empty(), api(KEY).route(MEETING_POINT, VENUE_WITH_COORDINATES));
    }

    @Test
    void otherHttpStatusIsUnavailable() {
        directionsStatus.set(429);
        assertThrows(RoutingUnavailable.class, () -> api(KEY).route(MEETING_POINT, VENUE_WITH_COORDINATES));

        geocodeStatus.set(403);
        assertThrows(RoutingUnavailable.class, () -> api(KEY).route(MEETING_POINT, VENUE_WITH_COORDINATES));
    }

    @Test
    void unexpectedAnswerIsUnavailable() {
        directionsFile.set("unexpected.json");
        assertThrows(RoutingUnavailable.class, () -> api(KEY).route(MEETING_POINT, VENUE_WITH_COORDINATES));

        geocodeFile.set("unexpected.json");
        assertThrows(RoutingUnavailable.class, () -> api(KEY).route(MEETING_POINT, VENUE_WITH_COORDINATES));
    }

    @Test
    void withoutKeyNothingIsAskedAndItIsUnavailable() {
        assertThrows(RoutingUnavailable.class, () -> api("").route(MEETING_POINT, VENUE_WITH_COORDINATES));
        assertTrue(requests.isEmpty());
    }

    @Test
    void unreachableServiceIsUnavailable() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        OrsApi api = new OrsApi(URI.create("http://127.0.0.1:" + port), KEY, Duration.ofSeconds(2));
        assertThrows(RoutingUnavailable.class, () -> api.route(MEETING_POINT, VENUE_WITH_COORDINATES));
    }
}
