package ch.stvbero.platform.core.driver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DRV-15: routes from Google Maps (Routes API), through a local HTTP server. The answers in
 * src/test/resources/google-routes follow the documented format; they are not recorded, because there is no key
 * yet (O-64). The key goes in the header, never into the address.
 */
class GoogleRoutesApiTest {

    private static final String KEY = "google-test-key";
    private static final Place HOME = new Place("Oberstufenschulhaus Beromünster", "Oberstufenschulhaus Beromünster",
            null, null);
    private static final Place VENUE = new Place("AL arena", "AL arena, Gossau ZH", 47.299515, 8.768488);

    private HttpServer server;
    private final AtomicReference<String> file = new AtomicReference<>("route.json");
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<URI> request = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();
    private final AtomicReference<String> fieldMask = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/directions/v2:computeRoutes", exchange -> {
            request.set(exchange.getRequestURI());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("X-Goog-Api-Key"));
            fieldMask.set(exchange.getRequestHeaders().getFirst("X-Goog-FieldMask"));
            byte[] content;
            try (InputStream in = GoogleRoutesApiTest.class.getResourceAsStream("/google-routes/" + file.get())) {
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

    private GoogleRoutesApi api() {
        return new GoogleRoutesApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), KEY,
                Duration.ofSeconds(5));
    }

    @Test
    void sendsTextOrCoordinatesAndReadsDistanceAndDuration() throws IOException {
        Optional<Routing.Result> route = api().route(HOME, VENUE);

        assertEquals(Optional.of(new Routing.Result(89312, 4712)), route);
        assertEquals(KEY, key.get());
        assertFalse(request.get().toString().contains(KEY), "key in the address");
        assertEquals("routes.distanceMeters,routes.duration", fieldMask.get());
        JsonNode sent = new ObjectMapper().readTree(body.get());
        // A place without coordinates goes as text, with the country; no separate geocoder is needed
        assertEquals("Oberstufenschulhaus Beromünster, Schweiz", sent.path("origin").path("address").asText());
        assertEquals(47.299515, sent.path("destination").path("location").path("latLng").path("latitude").asDouble());
        assertEquals(8.768488, sent.path("destination").path("location").path("latLng").path("longitude").asDouble());
        assertEquals("DRIVE", sent.path("travelMode").asText());
    }

    @Test
    void answerWithoutRoutesGivesNoRoute() {
        file.set("empty.json");
        assertEquals(Optional.empty(), api().route(HOME, VENUE));
    }

    @Test
    void failuresCountAsUnavailable() {
        status.set(403);
        assertThrows(RoutingUnavailable.class, () -> api().route(HOME, VENUE));
        server.stop(0);
        assertThrows(RoutingUnavailable.class, () -> api().route(HOME, VENUE));
    }

    @Test
    void durationsAreSecondsWithAnS() {
        assertEquals(4712, GoogleRoutesApi.seconds("4712s"));
        assertEquals(1235, GoogleRoutesApi.seconds("1234.5s"));
        assertNull(GoogleRoutesApi.seconds("PT20M"));
        assertNull(GoogleRoutesApi.seconds(""));
    }

    @Test
    void firstConfiguredServiceIsUsed() {
        assertEquals(LiveRouting.Service.GOOGLE, LiveRouting.service("google-key", "ors-key"));
        assertEquals(LiveRouting.Service.OPEN_ROUTE_SERVICE, LiveRouting.service(" ", "ors-key"));
        // Without any key a route is still calculated (DRV-15)
        assertEquals(LiveRouting.Service.OPEN_STREET_MAP, LiveRouting.service("", ""));
    }
}
