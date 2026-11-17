package ch.stvbero.platform.core.swissunihockey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * HTTP access to the swiss unihockey API. Without CDI, so that the test can run it against a local server
 * with recorded responses (F-07, criterion 2).
 * <p>
 * It fetches the match list of a team ({@code GET <base>/games?mode=team&team_id=…&season=…}) and the league table
 * ({@code GET <base>/rankings?team_id=…&season=…}, F-17).
 * Every failure (network, timeout, HTTP status other than 200, no JSON, unexpected table) becomes
 * {@link SuUnavailable} (SUH-09).
 */
public class SuApi {

    private final URI base;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /** @param base e.g. {@code https://api-v2.swissunihockey.ch/api}, without a trailing slash */
    public SuApi(URI base, Duration timeout) {
        this.base = base;
        this.timeout = timeout;
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

    private JsonNode get(String pathAndQuery) {
        URI uri = URI.create(base.toString().replaceAll("/+$", "") + pathAndQuery);
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
        try {
            return json.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new SuUnavailable("Unexpected response from swiss unihockey: not JSON", e);
        }
    }
}
