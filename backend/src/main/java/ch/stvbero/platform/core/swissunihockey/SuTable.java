package ch.stvbero.platform.core.swissunihockey;

import ch.stvbero.platform.core.clock.BackendClock;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Übersetzt die Spielliste von swiss unihockey ({@code GET /games?mode=team}) in {@link SuSpiel}e.
 * <p>
 * Die Schnittstelle liefert keine fachlichen Objekte, sondern eine generische Tabelle
 * ({@code data.regions[].rows[].cells[].text[]}). Pro Zeile ein Spiel, die Spiel-ID im Link der Zeile
 * ({@code link.ids[0]}). Spalten nach Position: 0 Datum und Zeit, 1 Halle und Ort (mit Kartenlink), 2 Heimteam,
 * 3 Gastteam, 4 Resultat. So lesen auch die öffentlichen Clients der Schnittstelle die Tabelle (O-21).
 * <p>
 * Weicht die Antwort von dieser Form ab, gilt sie als unerwartet ({@link SuNichtErreichbar}, SUH-09): Lieber
 * nichts ändern als falsche Spieltage anlegen.
 */
final class SuTable {

    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern RESULT = Pattern.compile("(\\d{1,2})\\s*:\\s*(\\d{1,2})");
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("d.M.yyyy");

    private SuTable() {
    }

    /**
     * @param antwort ganze JSON-Antwort
     * @param heute   für die relativen Angaben «heute», «gestern», «morgen», die swiss unihockey statt eines
     *                Datums liefert
     */
    static List<SuMatch> matches(JsonNode response, LocalDate today) {
        JsonNode regions = response.path("data").path("regions");
        if (!regions.isArray()) {
            throw unexpected("Antwort ohne data.regions");
        }
        List<SuMatch> matches = new ArrayList<>();
        for (JsonNode region : regions) {
            for (JsonNode row : region.path("rows")) {
                JsonNode id = row.path("link").path("ids").path(0);
                if (!id.canConvertToInt() && !id.isTextual()) {
                    // Zwischenzeilen ohne Spiel (z.B. Überschriften) überspringen
                    continue;
                }
                matches.add(match(row, id, today));
            }
        }
        return matches;
    }

    private static SuMatch match(JsonNode row, JsonNode id, LocalDate today) {
        JsonNode cells = row.path("cells");
        if (cells.size() < 4) {
            throw unexpected("Spielzeile mit " + cells.size() + " statt mindestens 4 Spalten");
        }
        int suMatchId;
        try {
            suMatchId = id.isTextual() ? Integer.parseInt(id.asText().strip()) : id.asInt();
        } catch (NumberFormatException e) {
            throw unexpected("Spiel-ID «" + id.asText() + "» ist keine Zahl");
        }
        LocalDateTime startTime = startTime(text(cells.path(0)), today, suMatchId);
        List<String> locationTexts = texts(cells.path(1));
        String hall = locationTexts.isEmpty() ? "" : locationTexts.get(0);
        if (hall.isBlank()) {
            throw unexpected("Spiel " + suMatchId + " ohne Halle");
        }
        String location = locationTexts.size() > 1 && !locationTexts.get(1).isBlank() ? locationTexts.get(1) : null;
        JsonNode map = cells.path(1).path("link");
        String mapsUrl = map.path("url").isTextual() ? map.path("url").asText() : null;
        Double longitude = number(map.path("x"));
        Double latitude = number(map.path("y"));
        String home = text(cells.path(2));
        String away = text(cells.path(3));
        if (home.isBlank() || away.isBlank()) {
            throw unexpected("Spiel " + suMatchId + " ohne Heim- oder Gastteam");
        }
        String result = null;
        if (cells.size() > 4) {
            Matcher r = RESULT.matcher(text(cells.path(4)));
            if (r.find()) {
                result = Integer.parseInt(r.group(1)) + ":" + Integer.parseInt(r.group(2));
            }
        }
        return new SuMatch(suMatchId, startTime.atZone(BackendClock.ZONE).toInstant(), home, away, hall, location, mapsUrl,
                latitude, longitude, result);
    }

    private static LocalDateTime startTime(String text, LocalDate today, int suMatchId) {
        String lower = text.toLowerCase();
        LocalDate day;
        int from = 0;
        Matcher d = DATE.matcher(text);
        if (d.find()) {
            try {
                day = LocalDate.parse(d.group(1) + "." + d.group(2) + "." + d.group(3), TAG);
            } catch (DateTimeException e) {
                throw unexpected("Spiel " + suMatchId + ": Datum «" + text + "» nicht lesbar");
            }
            from = d.end();
        } else if (lower.contains("heute")) {
            day = today;
        } else if (lower.contains("gestern")) {
            day = today.minusDays(1);
        } else if (lower.contains("morgen")) {
            day = today.plusDays(1);
        } else {
            throw unexpected("Spiel " + suMatchId + ": Datum «" + text + "» nicht lesbar");
        }
        // Die Zeit steht nach dem Datum, z.B. «22.11.2026 09:30»
        Matcher z = TIME.matcher(text.substring(from));
        if (!z.find()) {
            throw unexpected("Spiel " + suMatchId + ": Anspielzeit «" + text + "» nicht lesbar");
        }
        try {
            return LocalDateTime.of(day, LocalTime.of(Integer.parseInt(z.group(1)), Integer.parseInt(z.group(2))));
        } catch (DateTimeException e) {
            throw unexpected("Spiel " + suMatchId + ": Anspielzeit «" + text + "» nicht lesbar");
        }
    }

    /** Alle Texte einer Zelle, mit Leerzeichen verbunden. */
    private static String text(JsonNode cell) {
        return String.join(" ", texts(cell)).strip();
    }

    private static List<String> texts(JsonNode cell) {
        List<String> texts = new ArrayList<>();
        JsonNode t = cell.path("text");
        if (t.isArray()) {
            t.forEach(x -> texts.add(x.isNull() ? "" : x.asText().strip()));
        } else if (t.isTextual()) {
            texts.add(t.asText().strip());
        }
        return texts;
    }

    private static Double number(JsonNode value) {
        if (value.isNumber()) {
            return value.asDouble();
        }
        if (value.isTextual()) {
            try {
                return Double.parseDouble(value.asText().strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static SuUnavailable unexpected(String reason) {
        return new SuUnavailable("Unerwartete Antwort von swiss unihockey: " + reason);
    }
}
