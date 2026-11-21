package ch.stvbero.platform.core.swissunihockey;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates the swiss unihockey league table ({@code GET /rankings?team_id=…&season=…}) into {@link SuStanding}
 * rows (F-17, criterion 8).
 * <p>
 * Like the match list, the API delivers a generic table: {@code data.headers[].text} names the columns,
 * {@code data.regions[].rows[].cells[].text[]} holds the values, {@code data.title} the league and
 * {@code data.subtitle} the group. The columns are found by their header, not by position, because the table has
 * more columns for leagues with overtime: «Rg» (rank), «Team», «Sp» (matches), «S» (wins), «SnV» (wins after
 * overtime, counted as wins), «U» (draws), «NnV» (losses after overtime, counted as losses), «N» (losses), «T»
 * (goals «for:against»), «P» (points). The team ID comes from {@code rows[].data.team.id} or the row link
 * (assumption F-17, O-59: not recorded live).
 * <p>
 * Without rank, team or points the response counts as unexpected ({@link SuUnavailable}, SUH-09).
 */
final class SuRanking {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+");
    private static final Pattern GOALS = Pattern.compile("(\\d+)\\s*:\\s*(\\d+)");
    private static final Pattern GROUP = Pattern.compile("^(.*?)[\\s,–-]+(Gruppe\\s+.+)$");

    private SuRanking() {
    }

    /** League, group and table; the matches are fetched separately ({@link SuLeague#matches()} empty). */
    static SuLeague league(JsonNode response) {
        JsonNode data = response.path("data");
        JsonNode regions = data.path("regions");
        JsonNode headers = data.path("headers");
        if (!regions.isArray() || !headers.isArray()) {
            throw unexpected("response without data.headers or data.regions");
        }
        Map<String, Integer> columns = columns(headers);
        for (String required : List.of("rank", "team", "points")) {
            if (!columns.containsKey(required)) {
                throw unexpected("table without column " + required);
            }
        }
        List<SuStanding> table = new ArrayList<>();
        for (JsonNode region : regions) {
            for (JsonNode row : region.path("rows")) {
                table.add(standing(row, columns));
            }
        }
        String league = leagueName(text(data.path("title")));
        String group = text(data.path("subtitle"));
        if (group == null && league != null) {
            // Some responses put the group into the title, e.g. «Herren Kleinfeld 3. Liga Gruppe 12»
            Matcher m = GROUP.matcher(league);
            if (m.matches()) {
                league = m.group(1).strip();
                group = m.group(2).strip();
            }
        }
        return new SuLeague(league, group, table, List.of());
    }

    private static final Pattern TITLE_PREFIX = Pattern.compile("^Tabelle\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_SUFFIX = Pattern.compile(
            "(\\s+\\d{4}/\\d{2,4})?(\\s+per\\s+\\d{1,2}\\.\\d{1,2}\\.\\d{2,4})?\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * The league name without what the real interface puts around it: «Tabelle Damen KF 1. Liga Gruppe 2 2026/27
     * per 21.11.2026» becomes «Damen KF 1. Liga Gruppe 2» (recorded live on 21.11.2026).
     */
    static String leagueName(String title) {
        if (title == null) {
            return null;
        }
        String name = TITLE_SUFFIX.matcher(TITLE_PREFIX.matcher(title.strip()).replaceFirst("")).replaceFirst("")
                .strip();
        return name.isEmpty() ? null : name;
    }

    /** Column index by meaning, from the header texts. */
    private static Map<String, Integer> columns(JsonNode headers) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            JsonNode header = headers.get(i);
            String text = header.isTextual() ? header.asText() : String.join(" ", texts(header.path("text")));
            String key = switch (text.strip().replace(".", "").toLowerCase(Locale.ROOT)) {
                case "rg", "rang", "rank", "#" -> "rank";
                case "team", "mannschaft" -> "team";
                case "sp", "spiele" -> "matches";
                case "s", "siege" -> "wins";
                case "snv" -> "winsOvertime";
                case "u", "unentschieden" -> "draws";
                case "nnv" -> "lossesOvertime";
                case "n", "niederlagen" -> "losses";
                case "t", "tore" -> "goals";
                case "p", "pkt", "punkte" -> "points";
                default -> null;
            };
            if (key != null) {
                columns.putIfAbsent(key, i);
            }
        }
        return columns;
    }

    private static SuStanding standing(JsonNode row, Map<String, Integer> columns) {
        JsonNode cells = row.path("cells");
        String team = cell(cells, columns.get("team"));
        if (team.isBlank()) {
            throw unexpected("table row without a team");
        }
        Integer rank = number(cell(cells, columns.get("rank")));
        Integer points = number(cell(cells, columns.get("points")));
        if (rank == null || points == null) {
            throw unexpected("table row of '" + team + "' without rank or points");
        }
        int goalsFor = 0;
        int goalsAgainst = 0;
        Matcher goals = GOALS.matcher(cell(cells, columns.get("goals")));
        if (goals.find()) {
            goalsFor = Integer.parseInt(goals.group(1));
            goalsAgainst = Integer.parseInt(goals.group(2));
        }
        return new SuStanding(rank, teamId(row), team, value(cells, columns, "matches"),
                value(cells, columns, "wins") + value(cells, columns, "winsOvertime"),
                value(cells, columns, "draws"),
                value(cells, columns, "losses") + value(cells, columns, "lossesOvertime"),
                goalsFor, goalsAgainst, points);
    }

    private static Integer teamId(JsonNode row) {
        JsonNode id = row.path("data").path("team").path("id");
        if (id.isMissingNode() || id.isNull()) {
            JsonNode link = row.path("link");
            id = "team_detail".equals(link.path("page").asText()) ? link.path("ids").path(0) : id;
        }
        if (id.canConvertToInt()) {
            return id.asInt();
        }
        return id.isTextual() ? number(id.asText()) : null;
    }

    private static int value(JsonNode cells, Map<String, Integer> columns, String key) {
        Integer number = number(cell(cells, columns.get(key)));
        return number == null ? 0 : number;
    }

    private static String cell(JsonNode cells, Integer index) {
        if (index == null) {
            return "";
        }
        return String.join(" ", texts(cells.path(index).path("text"))).strip();
    }

    private static Integer number(String text) {
        Matcher m = NUMBER.matcher(text);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    private static String text(JsonNode node) {
        String text = String.join(" ", texts(node)).strip();
        return text.isEmpty() ? null : text;
    }

    private static List<String> texts(JsonNode node) {
        List<String> texts = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(x -> texts.add(x.isNull() ? "" : x.asText().strip()));
        } else if (node.isTextual() || node.isNumber()) {
            texts.add(node.asText().strip());
        }
        return texts;
    }

    private static SuUnavailable unexpected(String reason) {
        return new SuUnavailable("Unexpected league table from swiss unihockey: " + reason);
    }
}
