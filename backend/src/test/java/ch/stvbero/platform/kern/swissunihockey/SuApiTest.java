package ch.stvbero.platform.kern.swissunihockey;

import ch.stvbero.platform.kern.uhr.Uhr;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-07, Kriterium 2: Der echte Client gegen aufgezeichnete Antworten (src/test/resources/swissunihockey), über einen
 * lokalen HTTP-Server statt swiss unihockey. Störungen werden zu {@link SuNichtErreichbar} (SUH-09).
 */
class SuApiTest {

    private static final LocalDate HEUTE = LocalDate.of(2026, 11, 16);

    private HttpServer server;
    private final AtomicReference<String> datei = new AtomicReference<>("games-team.json");
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<URI> anfrage = new AtomicReference<>();

    @BeforeEach
    void starten() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/games", austausch -> {
            anfrage.set(austausch.getRequestURI());
            byte[] inhalt;
            try (InputStream in = SuApiTest.class.getResourceAsStream("/swissunihockey/" + datei.get())) {
                inhalt = in.readAllBytes();
            }
            austausch.getResponseHeaders().add("Content-Type", "application/json");
            austausch.sendResponseHeaders(status.get(), inhalt.length);
            try (OutputStream out = austausch.getResponseBody()) {
                out.write(inhalt);
            }
        });
        server.start();
    }

    @AfterEach
    void stoppen() {
        server.stop(0);
    }

    private SuApi api() {
        return new SuApi(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/"),
                Duration.ofSeconds(5));
    }

    @Test
    void suh01SpiellisteEinesTeamsWirdGelesen() {
        List<SuSpiel> spiele = api().spiele(900001, 2026, HEUTE);

        assertEquals("/api/games", anfrage.get().getPath());
        String query = anfrage.get().getQuery();
        assertTrue(query.contains("mode=team") && query.contains("team_id=900001") && query.contains("season=2026"),
                query);

        assertEquals(3, spiele.size(), "Zwischenzeile ohne Spiel wird übersprungen");
        SuSpiel erstes = spiele.get(0);
        assertEquals(5000001, erstes.suSpielId());
        assertEquals(LocalDateTime.of(2026, 11, 22, 9, 30).atZone(Uhr.ZONE).toInstant(), erstes.anspielzeit());
        assertEquals("STV Beromünster I", erstes.heimteam());
        assertEquals("UHC Musterdorf", erstes.gastteam());
        assertEquals("Sporthalle Seefeld", erstes.halle());
        assertEquals("Musterdorf", erstes.ort());
        assertEquals(47.2141, erstes.breite());
        assertEquals(8.1662, erstes.laenge());
        assertNull(erstes.resultat(), "Leeres Resultat");

        SuSpiel drittes = spiele.get(2);
        assertEquals(5000003, drittes.suSpielId(), "Spiel-ID auch als Text");
        assertEquals("3:5", drittes.resultat());
        assertNull(drittes.breite());
    }

    @Test
    void relativeDatumsangabenWerdenAufgeloest() {
        datei.set("games-relativ.json");
        List<SuSpiel> spiele = api().spiele(900001, 2026, HEUTE);

        assertEquals(LocalDateTime.of(2026, 11, 16, 19, 15).atZone(Uhr.ZONE).toInstant(), spiele.get(0).anspielzeit());
        assertEquals("2:2", spiele.get(0).resultat(), "Zusatz «n.V.» fällt weg");
        assertEquals(LocalDateTime.of(2026, 11, 17, 10, 0).atZone(Uhr.ZONE).toInstant(), spiele.get(1).anspielzeit());
        assertNull(spiele.get(1).resultat(), "«-» ist kein Resultat");
    }

    @Test
    void suh09UnerwarteteAntwort() {
        datei.set("games-unerwartet.json");
        SuNichtErreichbar f = assertThrows(SuNichtErreichbar.class, () -> api().spiele(900001, 2026, HEUTE));
        assertTrue(f.getMessage().contains("Unerwartete Antwort"), f.getMessage());
    }

    @Test
    void suh09HttpFehler() {
        status.set(401);
        SuNichtErreichbar f = assertThrows(SuNichtErreichbar.class, () -> api().spiele(900001, 2026, HEUTE));
        assertTrue(f.getMessage().contains("HTTP 401"), f.getMessage());
    }

    @Test
    void suh09NichtErreichbar() throws IOException {
        int freierPort;
        try (ServerSocket s = new ServerSocket(0)) {
            freierPort = s.getLocalPort();
        }
        SuApi weg = new SuApi(URI.create("http://127.0.0.1:" + freierPort + "/api"), Duration.ofSeconds(2));
        assertThrows(SuNichtErreichbar.class, () -> weg.spiele(900001, 2026, HEUTE));
    }
}
