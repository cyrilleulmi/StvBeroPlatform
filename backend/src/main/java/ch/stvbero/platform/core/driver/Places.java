package ch.stvbero.platform.core.driver;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the places of a route from what a match day stores (DRV-12). Pure rules without CDI.
 * <p>
 * Venue, in this order:
 * <ol>
 *     <li>the stored coordinates of the venue,</li>
 *     <li>coordinates in the Google Maps link: {@code @47.1,8.2}, {@code !3d47.1!4d8.2}, or a parameter
 *     {@code query}, {@code q}, {@code ll}, {@code destination}, {@code daddr} with «lat,lon»,</li>
 *     <li>otherwise the text for the geocoder: the search text of the link ({@code query}/{@code q}/
 *     {@code destination}/{@code daddr}, or the place name in {@code /maps/place/<name>/}), otherwise
 *     «name, address».</li>
 * </ol>
 * The meeting point is free text («Parkplatz Sporthalle Nord»); it is searched as it is. Short links
 * ({@code maps.app.goo.gl}) cannot be read without asking Google, so they fall back to name and address.
 */
public final class Places {

    private static final String NUMBER = "(-?\\d{1,3}(?:\\.\\d+)?)";
    private static final Pattern AT = Pattern.compile("@" + NUMBER + "," + NUMBER);
    private static final Pattern DATA = Pattern.compile("!3d" + NUMBER + "!4d" + NUMBER);
    private static final Pattern PAIR = Pattern.compile("^\\s*" + NUMBER + "\\s*,\\s*" + NUMBER + "\\s*$");
    private static final Pattern PLACE = Pattern.compile("/maps/place/([^/@?]+)");
    private static final String[] COORDINATE_PARAMETERS = {"query", "q", "ll", "destination", "daddr"};
    private static final String[] SEARCH_PARAMETERS = {"query", "q", "destination", "daddr"};

    private Places() {
    }

    /** The venue of a match day (location of the event). */
    public static Place venue(String name, String address, String mapsUrl, Double latitude, Double longitude) {
        String label = name.strip();
        String fallback = address == null || address.isBlank() ? label : label + ", " + address.strip();
        if (valid(latitude, longitude)) {
            return new Place(label, fallback, latitude, longitude);
        }
        Optional<double[]> fromLink = coordinates(mapsUrl);
        if (fromLink.isPresent()) {
            return new Place(label, fallback, fromLink.get()[0], fromLink.get()[1]);
        }
        return new Place(label, searchText(mapsUrl).orElse(fallback), null, null);
    }

    /** The meeting point of a match day: free text, searched as it is. */
    public static Place meetingPoint(String location) {
        String label = location.strip();
        return new Place(label, label, null, null);
    }

    /** Coordinates in a Google Maps link, if it holds any. */
    static Optional<double[]> coordinates(String mapsUrl) {
        if (mapsUrl == null || mapsUrl.isBlank()) {
            return Optional.empty();
        }
        // The place marker (!3d…!4d…) is more precise than the map centre (@…)
        for (Pattern pattern : new Pattern[] {DATA, AT}) {
            Matcher m = pattern.matcher(mapsUrl);
            if (m.find()) {
                Optional<double[]> found = pair(m.group(1), m.group(2));
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        for (String name : COORDINATE_PARAMETERS) {
            Optional<double[]> found = parameter(mapsUrl, name).map(PAIR::matcher).filter(Matcher::matches)
                    .flatMap(m -> pair(m.group(1), m.group(2)));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** Search text in a Google Maps link: a search parameter or the place name. */
    static Optional<String> searchText(String mapsUrl) {
        if (mapsUrl == null || mapsUrl.isBlank()) {
            return Optional.empty();
        }
        for (String name : SEARCH_PARAMETERS) {
            Optional<String> text = parameter(mapsUrl, name).filter(value -> !PAIR.matcher(value).matches());
            if (text.isPresent()) {
                return text;
            }
        }
        Matcher m = PLACE.matcher(mapsUrl);
        if (m.find()) {
            String name = decode(m.group(1)).strip();
            return name.isEmpty() ? Optional.empty() : Optional.of(name);
        }
        return Optional.empty();
    }

    private static Optional<String> parameter(String url, String name) {
        String query;
        try {
            query = URI.create(url.strip()).getRawQuery();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (query == null) {
            return Optional.empty();
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                String value = decode(part.substring(eq + 1)).strip();
                return value.isEmpty() ? Optional.empty() : Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static Optional<double[]> pair(String latitude, String longitude) {
        double lat = Double.parseDouble(latitude);
        double lon = Double.parseDouble(longitude);
        return valid(lat, lon) ? Optional.of(new double[] {lat, lon}) : Optional.empty();
    }

    private static boolean valid(Double latitude, Double longitude) {
        return latitude != null && longitude != null && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180 && !(latitude == 0 && longitude == 0);
    }
}
