package ch.stvbero.platform.driver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * HTTP access to OpenRouteService (DRV-06). Without CDI, so that the test can run it against a local server
 * with recorded answers (src/test/resources/openrouteservice).
 * <ul>
 *     <li>Places without coordinates are looked up first: {@code GET <base>/geocode/search?text=…&size=1
 *     &boundary.country=CH}, coordinates of the first hit ({@code features[0].geometry.coordinates}, lon/lat).
 *     No hit: no route.</li>
 *     <li>Route: {@code GET <base>/v2/directions/driving-car?start=<lon>,<lat>&end=<lon>,<lat>}, distance and
 *     duration from {@code features[0].properties.summary}. HTTP 404 (no routable point, no route): no route.</li>
 * </ul>
 * The key goes in the header {@code Authorization}, never into the address, so that it does not end up in logs.
 * Every other failure (network, timeout, other HTTP status, no JSON, unexpected structure) becomes
 * {@link RoutingUnavailable}.
 */
public class OrsApi {

    private final URI base;
    private final String key;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /** @param base e.g. {@code https://api.openrouteservice.org}, without a trailing slash */
    public OrsApi(URI base, String key, Duration timeout) {
        this.base = base;
        this.key = key;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<Routing.Result> route(Place from, Place to) {
        Optional<double[]> start = coordinates(from);
        if (start.isEmpty()) {
            return Optional.empty();
        }
        Optional<double[]> end = coordinates(to);
        if (end.isEmpty()) {
            return Optional.empty();
        }
        Optional<JsonNode> answer = get("/v2/directions/driving-car?start=" + lonLat(start.get())
                + "&end=" + lonLat(end.get()), true);
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        JsonNode summary = answer.get().path("features").path(0).path("properties").path("summary");
        if (!summary.path("distance").isNumber()) {
            // A route of 0 m (same place) has no distance field in the summary
            if (summary.isObject() && answer.get().path("features").size() > 0) {
                return Optional.of(new Routing.Result(0, (int) Math.round(summary.path("duration").asDouble(0))));
            }
            throw new RoutingUnavailable("Unexpected answer from OpenRouteService: no route summary");
        }
        return Optional.of(new Routing.Result((int) Math.round(summary.path("distance").asDouble()),
                (int) Math.round(summary.path("duration").asDouble())));
    }

    /** Coordinates (lat, lon) of the place: known ones, otherwise the first hit of the geocoder in Switzerland. */
    Optional<double[]> coordinates(Place place) {
        if (place.hasCoordinates()) {
            return Optional.of(new double[] {place.latitude(), place.longitude()});
        }
        JsonNode answer = get("/geocode/search?text=" + URLEncoder.encode(place.search(), StandardCharsets.UTF_8).replace("+", "%20")
                + "&size=1&boundary.country=CH", false).orElseThrow();
        JsonNode features = answer.path("features");
        if (!features.isArray()) {
            throw new RoutingUnavailable("Unexpected answer from the OpenRouteService geocoder: no features");
        }
        if (features.isEmpty()) {
            return Optional.empty();
        }
        JsonNode point = features.path(0).path("geometry").path("coordinates");
        if (!point.path(0).isNumber() || !point.path(1).isNumber()) {
            throw new RoutingUnavailable("Unexpected answer from the OpenRouteService geocoder: no coordinates");
        }
        return Optional.of(new double[] {point.path(1).asDouble(), point.path(0).asDouble()});
    }

    private static String lonLat(double[] latLon) {
        return String.format(Locale.ROOT, "%.6f,%.6f", latLon[1], latLon[0]);
    }

    /** @param notFoundIsEmpty HTTP 404 means «no result» instead of a failure */
    private Optional<JsonNode> get(String pathAndQuery, boolean notFoundIsEmpty) {
        if (key == null || key.isBlank()) {
            throw new RoutingUnavailable("OpenRouteService is not configured (STV_ORS_API_KEY)");
        }
        URI uri = URI.create(base.toString().replaceAll("/+$", "") + pathAndQuery);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Authorization", key)
                .header("Accept", "application/json, application/geo+json").GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RoutingUnavailable("OpenRouteService not reachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoutingUnavailable("Request to OpenRouteService interrupted", e);
        }
        if (response.statusCode() == 404 && notFoundIsEmpty) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new RoutingUnavailable("OpenRouteService answered with HTTP " + response.statusCode());
        }
        try {
            return Optional.of(json.readTree(response.body()));
        } catch (JsonProcessingException e) {
            throw new RoutingUnavailable("Unexpected answer from OpenRouteService: not JSON", e);
        }
    }
}
