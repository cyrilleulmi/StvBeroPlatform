package ch.stvbero.platform.core.swissunihockey;

import com.fasterxml.jackson.core.JsonProcessingException;
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
 * HTTP-Zugriff auf die Schnittstelle von swiss unihockey. Ohne CDI, damit der Test sie gegen einen lokalen
 * Server mit aufgezeichneten Antworten laufen lassen kann (F-07, Kriterium 2).
 * <p>
 * Abgefragt wird die Spielliste eines Teams: {@code GET <basis>/games?mode=team&team_id=…&season=…}.
 * Jede Störung (Netz, Zeitüberschreitung, HTTP-Status ausser 200, kein JSON, unerwartete Tabelle) wird zu
 * {@link SuNichtErreichbar} (SUH-09).
 */
public class SuApi {

    private final URI base;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /** @param basis z.B. {@code https://api-v2.swissunihockey.ch/api}, ohne abschliessenden Schrägstrich */
    public SuApi(URI base, Duration timeout) {
        this.base = base;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<SuMatch> matches(int suTeamId, int season, LocalDate today) {
        URI uri = URI.create(base.toString().replaceAll("/+$", "") + "/games?mode=team&team_id=" + suTeamId
                + "&season=" + season + "&games_per_page=500&locale=de_CH");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SuUnavailable("swiss unihockey nicht erreichbar: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SuUnavailable("Abfrage bei swiss unihockey unterbrochen", e);
        }
        if (response.statusCode() != 200) {
            throw new SuUnavailable("swiss unihockey antwortet mit HTTP " + response.statusCode());
        }
        try {
            return SuTable.matches(json.readTree(response.body()), today);
        } catch (JsonProcessingException e) {
            throw new SuUnavailable("Unerwartete Antwort von swiss unihockey: kein JSON", e);
        }
    }
}
