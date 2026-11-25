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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Routes from OpenStreetMap data without a key (DRV-15): Nominatim finds places, OSRM calculates the route.
 * Without CDI, so that the test can run it against a local server with recorded answers
 * (src/test/resources/osm).
 * <ul>
 *     <li>Places without coordinates: {@code GET <geocoder>/search?q=…&format=jsonv2&limit=1&countrycodes=ch},
 *     coordinates of the first hit ({@code [0].lat}, {@code [0].lon}). Nominatim knows towns and streets, but
 *     rarely the name of a hall: if the whole text finds nothing, the search is repeated without its first
 *     word, down to the last word («Parkplatz Oberstufenschulhaus Beromünster» → «Beromünster»). The route then
 *     starts in the middle of the town, which is close enough for a distance.</li>
 *     <li>Route: {@code GET <router>/route/v1/driving/<lon>,<lat>;<lon>,<lat>?overview=false}, distance in
 *     metres and duration in seconds from {@code routes[0]}. {@code code} other than {@code Ok}: no route.</li>
 * </ul>
 * Both services are free and ask for moderate use and an identifying user agent; the app asks once per match
 * day (DRV-05). Every failure (network, timeout, HTTP status other than 200, no JSON, unexpected structure)
 * becomes {@link RoutingUnavailable}.
 */
public class OsmApi {

    private final URI geocoder;
    private final URI router;
    private final String userAgent;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param geocoder e.g. {@code https://nominatim.openstreetmap.org}
     * @param router   e.g. {@code https://router.project-osrm.org}
     */
    public OsmApi(URI geocoder, URI router, String userAgent, Duration timeout) {
        this.geocoder = geocoder;
        this.router = router;
        this.userAgent = userAgent;
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
        JsonNode answer = get(router, "/route/v1/driving/" + lonLat(start.get()) + ";" + lonLat(end.get())
                + "?overview=false", true);
        if (answer == null || !"Ok".equals(answer.path("code").asText())) {
            return Optional.empty();
        }
        JsonNode route = answer.path("routes").path(0);
        if (!route.path("distance").isNumber() || !route.path("duration").isNumber()) {
            throw new RoutingUnavailable("Unexpected answer from OSRM: no distance or duration");
        }
        return Optional.of(new Routing.Result((int) Math.round(route.path("distance").asDouble()),
                (int) Math.round(route.path("duration").asDouble())));
    }

    /** Coordinates (lat, lon) of the place: known ones, otherwise the first hit of the geocoder in Switzerland. */
    Optional<double[]> coordinates(Place place) {
        if (place.hasCoordinates()) {
            return Optional.of(new double[] {place.latitude(), place.longitude()});
        }
        for (String search : searches(place.search())) {
            JsonNode answer = get(geocoder, "/search?q=" + URLEncoder.encode(search, StandardCharsets.UTF_8)
                    .replace("+", "%20") + "&format=jsonv2&limit=1&countrycodes=ch", false);
            if (!answer.isArray()) {
                throw new RoutingUnavailable("Unexpected answer from Nominatim: no list");
            }
            if (answer.isEmpty()) {
                continue;
            }
            JsonNode hit = answer.path(0);
            try {
                return Optional.of(new double[] {Double.parseDouble(hit.path("lat").asText()),
                        Double.parseDouble(hit.path("lon").asText())});
            } catch (NumberFormatException e) {
                throw new RoutingUnavailable("Unexpected answer from Nominatim: no coordinates");
            }
        }
        return Optional.empty();
    }

    /**
     * The text, then the text without its first word and without its first two words, finally its last word
     * (usually the town). A single word is only searched if it has at least five letters: «Nord» or «Halle» alone
     * would find some other place. At most four searches.
     */
    static List<String> searches(String text) {
        String full = text.strip();
        List<String> searches = new ArrayList<>(List.of(full));
        List<String> words = List.of(full.replace(",", " ").split(" +"));
        for (int from = 1; from < words.size() && from <= 2; from++) {
            add(searches, String.join(" ", words.subList(from, words.size())));
        }
        add(searches, words.get(words.size() - 1));
        return searches;
    }

    private static void add(List<String> searches, String search) {
        boolean singleShortWord = !search.contains(" ") && search.length() < 5;
        if (!search.isBlank() && !singleShortWord && !searches.contains(search)) {
            searches.add(search);
        }
    }

    private static String lonLat(double[] latLon) {
        return String.format(Locale.ROOT, "%.6f,%.6f", latLon[1], latLon[0]);
    }

    /** @param badRequestIsNoRoute OSRM answers 400 with a code when it finds no route or no road nearby */
    private JsonNode get(URI base, String pathAndQuery, boolean badRequestIsNoRoute) {
        URI uri = URI.create(base.toString().replaceAll("/+$", "") + pathAndQuery);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RoutingUnavailable("OpenStreetMap routing not reachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoutingUnavailable("Request to OpenStreetMap routing interrupted", e);
        }
        if (response.statusCode() == 400 && badRequestIsNoRoute) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new RoutingUnavailable("OpenStreetMap routing answered with HTTP " + response.statusCode());
        }
        try {
            return json.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new RoutingUnavailable("Unexpected answer from OpenStreetMap routing: not JSON", e);
        }
    }
}
