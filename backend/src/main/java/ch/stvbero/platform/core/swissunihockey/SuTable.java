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
 * Translates the swiss unihockey match list ({@code GET /games?mode=team}) into {@link SuMatch} records.
 * <p>
 * The API does not deliver domain objects but a generic table ({@code data.regions[].rows[].cells[].text[]}).
 * One match per row, the match ID in the link of the row ({@code link.ids[0]}). Columns by position: 0 date and
 * time, 1 hall and town (with a map link), 2 home team, 3 away team, 4 result. The public clients of the API
 * read the table the same way (O-21).
 * <p>
 * If the response deviates from this form, it counts as unexpected ({@link SuUnavailable}, SUH-09): better to
 * change nothing than to create wrong match days.
 */
final class SuTable {

    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern RESULT = Pattern.compile("(\\d{1,2})\\s*:\\s*(\\d{1,2})");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d.M.yyyy");

    private SuTable() {
    }

    /**
     * @param response the whole JSON response
     * @param today    for the relative values «heute», «gestern», «morgen» that swiss unihockey delivers instead
     *                 of a date
     */
    static List<SuMatch> matches(JsonNode response, LocalDate today) {
        JsonNode regions = response.path("data").path("regions");
        if (!regions.isArray()) {
            throw unexpected("response without data.regions");
        }
        List<SuMatch> matches = new ArrayList<>();
        for (JsonNode region : regions) {
            for (JsonNode row : region.path("rows")) {
                JsonNode id = row.path("link").path("ids").path(0);
                if (!id.canConvertToInt() && !id.isTextual()) {
                    // skip rows without a match in between (e.g. headings)
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
            throw unexpected("match row with " + cells.size() + " instead of at least 4 columns");
        }
        int suMatchId;
        try {
            suMatchId = id.isTextual() ? Integer.parseInt(id.asText().strip()) : id.asInt();
        } catch (NumberFormatException e) {
            throw unexpected("match ID '" + id.asText() + "' is not a number");
        }
        LocalDateTime startTime = startTime(text(cells.path(0)), today, suMatchId);
        List<String> locationTexts = texts(cells.path(1));
        String hall = locationTexts.isEmpty() ? "" : locationTexts.get(0);
        if (hall.isBlank()) {
            throw unexpected("match " + suMatchId + " without a hall");
        }
        String location = locationTexts.size() > 1 && !locationTexts.get(1).isBlank() ? locationTexts.get(1) : null;
        JsonNode map = cells.path(1).path("link");
        String mapsUrl = map.path("url").isTextual() ? map.path("url").asText() : null;
        Double longitude = number(map.path("x"));
        Double latitude = number(map.path("y"));
        String home = text(cells.path(2));
        String away = text(cells.path(3));
        if (home.isBlank() || away.isBlank()) {
            throw unexpected("match " + suMatchId + " without a home or away team");
        }
        String result = null;
        if (cells.size() > 4) {
            Matcher resultMatcher = RESULT.matcher(text(cells.path(4)));
            if (resultMatcher.find()) {
                result = Integer.parseInt(resultMatcher.group(1)) + ":" + Integer.parseInt(resultMatcher.group(2));
            }
        }
        return new SuMatch(suMatchId, startTime.atZone(BackendClock.ZONE).toInstant(), home, away, hall, location, mapsUrl,
                latitude, longitude, result);
    }

    private static LocalDateTime startTime(String text, LocalDate today, int suMatchId) {
        String lower = text.toLowerCase();
        LocalDate day;
        int timeFrom = 0;
        Matcher dateMatcher = DATE.matcher(text);
        if (dateMatcher.find()) {
            try {
                day = LocalDate.parse(dateMatcher.group(1) + "." + dateMatcher.group(2) + "." + dateMatcher.group(3), DAY);
            } catch (DateTimeException e) {
                throw unexpected("match " + suMatchId + ": date '" + text + "' not readable");
            }
            timeFrom = dateMatcher.end();
        } else if (lower.contains("heute")) {
            day = today;
        } else if (lower.contains("gestern")) {
            day = today.minusDays(1);
        } else if (lower.contains("morgen")) {
            day = today.plusDays(1);
        } else {
            throw unexpected("match " + suMatchId + ": date '" + text + "' not readable");
        }
        // The time comes after the date, e.g. «22.11.2026 09:30»
        Matcher timeMatcher = TIME.matcher(text.substring(timeFrom));
        if (!timeMatcher.find()) {
            throw unexpected("match " + suMatchId + ": start time '" + text + "' not readable");
        }
        try {
            return LocalDateTime.of(day, LocalTime.of(Integer.parseInt(timeMatcher.group(1)),
                    Integer.parseInt(timeMatcher.group(2))));
        } catch (DateTimeException e) {
            throw unexpected("match " + suMatchId + ": start time '" + text + "' not readable");
        }
    }

    /** All texts of a cell, joined with spaces. */
    private static String text(JsonNode cell) {
        return String.join(" ", texts(cell)).strip();
    }

    private static List<String> texts(JsonNode cell) {
        List<String> texts = new ArrayList<>();
        JsonNode text = cell.path("text");
        if (text.isArray()) {
            text.forEach(x -> texts.add(x.isNull() ? "" : x.asText().strip()));
        } else if (text.isTextual()) {
            texts.add(text.asText().strip());
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
        return new SuUnavailable("Unexpected response from swiss unihockey: " + reason);
    }
}
