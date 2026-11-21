package ch.stvbero.platform.core.driver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Routes from Google Maps (DRV-15): the Routes API, {@code POST <base>/directions/v2:computeRoutes}. Without CDI,
 * so that the test can run it against a local server (src/test/resources/google-routes).
 * <p>
 * Google needs no separate geocoder: a place is sent as coordinates ({@code location.latLng}) or as text
 * ({@code address}, with «Schweiz» appended). The answer is limited to distance and duration with the header
 * {@code X-Goog-FieldMask}; the key goes in the header {@code X-Goog-Api-Key}, never into the address. An answer
 * without routes means there is no route.
 * <p>
 * The key needs a Google Cloud project with a billing account; the Routes API has a free monthly quota, beyond
 * it Google charges per request (O-64). Not checked against the real service: there is no key yet.
 */
public class GoogleRoutesApi {

    private final URI base;
    private final String key;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /** @param base e.g. {@code https://routes.googleapis.com}, without a trailing slash */
    public GoogleRoutesApi(URI base, String key, Duration timeout) {
        this.base = base;
        this.key = key;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<Routing.Result> route(Place from, Place to) {
        ObjectNode body = json.createObjectNode();
        body.set("origin", waypoint(from));
        body.set("destination", waypoint(to));
        body.put("travelMode", "DRIVE");
        body.put("routingPreference", "TRAFFIC_UNAWARE");
        body.put("regionCode", "CH");
        URI uri = URI.create(base.toString().replaceAll("/+$", "") + "/directions/v2:computeRoutes");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", key)
                .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RoutingUnavailable("Google Routes not reachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoutingUnavailable("Request to Google Routes interrupted", e);
        }
        if (response.statusCode() != 200) {
            throw new RoutingUnavailable("Google Routes answered with HTTP " + response.statusCode());
        }
        JsonNode answer;
        try {
            answer = json.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new RoutingUnavailable("Unexpected answer from Google Routes: not JSON", e);
        }
        JsonNode route = answer.path("routes").path(0);
        if (route.isMissingNode()) {
            // «{}»: Google found no route between these places
            return Optional.empty();
        }
        Integer seconds = seconds(route.path("duration").asText(""));
        if (seconds == null) {
            throw new RoutingUnavailable("Unexpected answer from Google Routes: no duration");
        }
        // A route of 0 m has no distanceMeters field
        return Optional.of(new Routing.Result(route.path("distanceMeters").asInt(0), seconds));
    }

    private ObjectNode waypoint(Place place) {
        ObjectNode waypoint = json.createObjectNode();
        if (place.hasCoordinates()) {
            ObjectNode latLng = waypoint.putObject("location").putObject("latLng");
            latLng.put("latitude", place.latitude());
            latLng.put("longitude", place.longitude());
        } else {
            waypoint.put("address", place.search() + ", Schweiz");
        }
        return waypoint;
    }

    /** Google delivers durations as «1234s» or «1234.5s». */
    static Integer seconds(String duration) {
        if (!duration.endsWith("s")) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(duration.substring(0, duration.length() - 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
