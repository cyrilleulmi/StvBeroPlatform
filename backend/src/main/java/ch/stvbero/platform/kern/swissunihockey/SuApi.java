package ch.stvbero.platform.kern.swissunihockey;

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

    private final URI basis;
    private final Duration zeitlimit;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /** @param basis z.B. {@code https://api-v2.swissunihockey.ch/api}, ohne abschliessenden Schrägstrich */
    public SuApi(URI basis, Duration zeitlimit) {
        this.basis = basis;
        this.zeitlimit = zeitlimit;
        this.http = HttpClient.newBuilder().connectTimeout(zeitlimit).followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<SuSpiel> spiele(int suTeamId, int saison, LocalDate heute) {
        URI uri = URI.create(basis.toString().replaceAll("/+$", "") + "/games?mode=team&team_id=" + suTeamId
                + "&season=" + saison + "&games_per_page=500&locale=de_CH");
        HttpRequest anfrage = HttpRequest.newBuilder(uri).timeout(zeitlimit)
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> antwort;
        try {
            antwort = http.send(anfrage, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SuNichtErreichbar("swiss unihockey nicht erreichbar: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SuNichtErreichbar("Abfrage bei swiss unihockey unterbrochen", e);
        }
        if (antwort.statusCode() != 200) {
            throw new SuNichtErreichbar("swiss unihockey antwortet mit HTTP " + antwort.statusCode());
        }
        try {
            return SuTabelle.spiele(json.readTree(antwort.body()), heute);
        } catch (JsonProcessingException e) {
            throw new SuNichtErreichbar("Unerwartete Antwort von swiss unihockey: kein JSON", e);
        }
    }
}
