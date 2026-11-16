package ch.stvbero.platform.kern.swissunihockey;

import ch.stvbero.platform.kern.uhr.Uhr;
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
final class SuTabelle {

    private static final Pattern DATUM = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern ZEIT = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern RESULTAT = Pattern.compile("(\\d{1,2})\\s*:\\s*(\\d{1,2})");
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("d.M.yyyy");

    private SuTabelle() {
    }

    /**
     * @param antwort ganze JSON-Antwort
     * @param heute   für die relativen Angaben «heute», «gestern», «morgen», die swiss unihockey statt eines
     *                Datums liefert
     */
    static List<SuSpiel> spiele(JsonNode antwort, LocalDate heute) {
        JsonNode regionen = antwort.path("data").path("regions");
        if (!regionen.isArray()) {
            throw unerwartet("Antwort ohne data.regions");
        }
        List<SuSpiel> spiele = new ArrayList<>();
        for (JsonNode region : regionen) {
            for (JsonNode zeile : region.path("rows")) {
                JsonNode id = zeile.path("link").path("ids").path(0);
                if (!id.canConvertToInt() && !id.isTextual()) {
                    // Zwischenzeilen ohne Spiel (z.B. Überschriften) überspringen
                    continue;
                }
                spiele.add(spiel(zeile, id, heute));
            }
        }
        return spiele;
    }

    private static SuSpiel spiel(JsonNode zeile, JsonNode id, LocalDate heute) {
        JsonNode zellen = zeile.path("cells");
        if (zellen.size() < 4) {
            throw unerwartet("Spielzeile mit " + zellen.size() + " statt mindestens 4 Spalten");
        }
        int suSpielId;
        try {
            suSpielId = id.isTextual() ? Integer.parseInt(id.asText().strip()) : id.asInt();
        } catch (NumberFormatException e) {
            throw unerwartet("Spiel-ID «" + id.asText() + "» ist keine Zahl");
        }
        LocalDateTime anspielzeit = anspielzeit(text(zellen.path(0)), heute, suSpielId);
        List<String> ortTexte = texte(zellen.path(1));
        String halle = ortTexte.isEmpty() ? "" : ortTexte.get(0);
        if (halle.isBlank()) {
            throw unerwartet("Spiel " + suSpielId + " ohne Halle");
        }
        String ort = ortTexte.size() > 1 && !ortTexte.get(1).isBlank() ? ortTexte.get(1) : null;
        JsonNode karte = zellen.path(1).path("link");
        String mapsUrl = karte.path("url").isTextual() ? karte.path("url").asText() : null;
        Double laenge = zahl(karte.path("x"));
        Double breite = zahl(karte.path("y"));
        String heim = text(zellen.path(2));
        String gast = text(zellen.path(3));
        if (heim.isBlank() || gast.isBlank()) {
            throw unerwartet("Spiel " + suSpielId + " ohne Heim- oder Gastteam");
        }
        String resultat = null;
        if (zellen.size() > 4) {
            Matcher r = RESULTAT.matcher(text(zellen.path(4)));
            if (r.find()) {
                resultat = Integer.parseInt(r.group(1)) + ":" + Integer.parseInt(r.group(2));
            }
        }
        return new SuSpiel(suSpielId, anspielzeit.atZone(Uhr.ZONE).toInstant(), heim, gast, halle, ort, mapsUrl,
                breite, laenge, resultat);
    }

    private static LocalDateTime anspielzeit(String text, LocalDate heute, int suSpielId) {
        String klein = text.toLowerCase();
        LocalDate tag;
        int ab = 0;
        Matcher d = DATUM.matcher(text);
        if (d.find()) {
            try {
                tag = LocalDate.parse(d.group(1) + "." + d.group(2) + "." + d.group(3), TAG);
            } catch (DateTimeException e) {
                throw unerwartet("Spiel " + suSpielId + ": Datum «" + text + "» nicht lesbar");
            }
            ab = d.end();
        } else if (klein.contains("heute")) {
            tag = heute;
        } else if (klein.contains("gestern")) {
            tag = heute.minusDays(1);
        } else if (klein.contains("morgen")) {
            tag = heute.plusDays(1);
        } else {
            throw unerwartet("Spiel " + suSpielId + ": Datum «" + text + "» nicht lesbar");
        }
        // Die Zeit steht nach dem Datum, z.B. «22.11.2026 09:30»
        Matcher z = ZEIT.matcher(text.substring(ab));
        if (!z.find()) {
            throw unerwartet("Spiel " + suSpielId + ": Anspielzeit «" + text + "» nicht lesbar");
        }
        try {
            return LocalDateTime.of(tag, LocalTime.of(Integer.parseInt(z.group(1)), Integer.parseInt(z.group(2))));
        } catch (DateTimeException e) {
            throw unerwartet("Spiel " + suSpielId + ": Anspielzeit «" + text + "» nicht lesbar");
        }
    }

    /** Alle Texte einer Zelle, mit Leerzeichen verbunden. */
    private static String text(JsonNode zelle) {
        return String.join(" ", texte(zelle)).strip();
    }

    private static List<String> texte(JsonNode zelle) {
        List<String> texte = new ArrayList<>();
        JsonNode t = zelle.path("text");
        if (t.isArray()) {
            t.forEach(x -> texte.add(x.isNull() ? "" : x.asText().strip()));
        } else if (t.isTextual()) {
            texte.add(t.asText().strip());
        }
        return texte;
    }

    private static Double zahl(JsonNode wert) {
        if (wert.isNumber()) {
            return wert.asDouble();
        }
        if (wert.isTextual()) {
            try {
                return Double.parseDouble(wert.asText().strip());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static SuNichtErreichbar unerwartet(String grund) {
        return new SuNichtErreichbar("Unerwartete Antwort von swiss unihockey: " + grund);
    }
}
