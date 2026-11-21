package ch.stvbero.platform.core.swissunihockey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * HTTP access to the swiss unihockey API. Without CDI, so that the test can run it against a local server
 * with recorded responses (F-07, criterion 2).
 * <p>
 * It fetches the match list of a team ({@code GET <base>/games?mode=team&team_id=…&season=…}), the league table
 * ({@code GET <base>/rankings?team_id=…&season=…}, F-17) and the teams of the club
 * ({@code GET <base>/teams?mode=by_club&club_id=…&season=…}, SUH-02).
 * Every failure (network, timeout, HTTP status other than 200, no JSON, unexpected table) becomes
 * {@link SuUnavailable} (SUH-09).
 * <p>
 * With a cache directory, a response is reused for {@code cacheTtl} instead of asking again (SUH-14): in
 * {@code dev} every restart loads the club data, and the free interface should not be asked each time.
 */
public class SuApi {

    private final URI base;
    private final Duration timeout;
    private final Path cacheDir;
    private final Duration cacheTtl;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /** @param base e.g. {@code https://wc.swissunihockey.ch}, without a trailing slash */
    public SuApi(URI base, Duration timeout) {
        this(base, timeout, null, Duration.ZERO);
    }

    /**
     * @param cacheDir directory for cached responses, or {@code null} for no cache
     * @param cacheTtl how long a cached response is reused
     */
    public SuApi(URI base, Duration timeout, Path cacheDir, Duration cacheTtl) {
        this.base = base;
        this.timeout = timeout;
        this.cacheDir = cacheDir;
        this.cacheTtl = cacheTtl;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<SuMatch> matches(int suTeamId, int season, LocalDate today) {
        return SuTable.matches(get("/games?mode=team&team_id=" + suTeamId + "&season=" + season
                + "&games_per_page=500&locale=de_CH"), today);
    }

    /**
     * League data of a team (F-17): the league table ({@code GET <base>/rankings?team_id=…&season=…}) and the
     * match list as for the sync.
     */
    public SuLeague league(int suTeamId, int season, LocalDate today) {
        SuLeague ranking = SuRanking.league(get("/rankings?team_id=" + suTeamId + "&season=" + season
                + "&locale=de_CH"));
        return new SuLeague(ranking.league(), ranking.group(), ranking.table(), matches(suTeamId, season, today));
    }

    /**
     * The teams of a club in a season (SUH-02). The answer is a «dropdown»:
     * {@code entries[].text} is the name, {@code entries[].set_in_context.team_id} the team ID (recorded live on
     * 21.11.2026).
     */
    public List<SuClubTeam> clubTeams(int clubId, int season) {
        return clubTeams(get("/teams?mode=by_club&club_id=" + clubId + "&season=" + season + "&locale=de_CH"));
    }

    static List<SuClubTeam> clubTeams(JsonNode response) {
        JsonNode entries = response.path("entries");
        if (!entries.isArray()) {
            throw new SuUnavailable("Unexpected response from swiss unihockey: team list without entries");
        }
        List<SuClubTeam> teams = new ArrayList<>();
        for (JsonNode entry : entries) {
            JsonNode id = entry.path("set_in_context").path("team_id");
            String name = entry.path("text").asText("").strip();
            if (id.canConvertToInt() && id.asInt() > 0 && !name.isEmpty()) {
                teams.add(new SuClubTeam(id.asInt(), name));
            }
        }
        return teams;
    }

    private JsonNode get(String pathAndQuery) {
        URI uri = URI.create(base.toString().replaceAll("/+$", "") + pathAndQuery);
        String cached = cached(uri);
        if (cached != null) {
            try {
                return json.readTree(cached);
            } catch (JsonProcessingException e) {
                // unreadable cache entry: ask again
            }
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SuUnavailable("swiss unihockey not reachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SuUnavailable("Request to swiss unihockey interrupted", e);
        }
        if (response.statusCode() != 200) {
            throw new SuUnavailable("swiss unihockey answered with HTTP " + response.statusCode());
        }
        JsonNode tree;
        try {
            tree = json.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new SuUnavailable("Unexpected response from swiss unihockey: not JSON", e);
        }
        store(uri, response.body());
        return tree;
    }

    // ------------------------------------------------------------------ Cache (SUH-14)

    private String cached(URI uri) {
        if (cacheDir == null) {
            return null;
        }
        Path file = cacheDir.resolve(fileName(uri));
        try {
            if (Files.isRegularFile(file)
                    && Files.getLastModifiedTime(file).toInstant().plus(cacheTtl).isAfter(Instant.now())) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // no usable cache entry: ask swiss unihockey
        }
        return null;
    }

    private void store(URI uri, String body) {
        if (cacheDir == null) {
            return;
        }
        try {
            Files.createDirectories(cacheDir);
            Files.writeString(cacheDir.resolve(fileName(uri)), body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // the cache is a convenience; without it the next call simply asks again
        }
    }

    private static String fileName(URI uri) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(uri.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16) + ".json";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
