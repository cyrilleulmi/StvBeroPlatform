package ch.stvbero.platform.kern.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F-00, Kriterium 9: Die von Quarkus erzeugte OpenAPI entspricht docs/api/openapi.yaml für alle
 * umgesetzten Pfade. Geprüft werden Pfade, Methoden, operationId, Statuscodes und die Schemas der
 * Antworten und Anfragen (Felder, Typen, Enum-Werte, rekursiv).
 */
@QuarkusTest
class OpenApiVertragTest {

    private static final Set<String> METHODEN = Set.of("get", "put", "post", "delete", "patch");
    private static final int MAX_TIEFE = 10;

    JsonNode vertrag;
    JsonNode erzeugt;

    @BeforeEach
    void laden() throws IOException {
        Path datei = Path.of("..", "docs", "api", "openapi.yaml");
        vertrag = new YAMLMapper().readTree(Files.readString(datei));
        erzeugt = new ObjectMapper().readTree(given().get("/q/openapi?format=json").then().statusCode(200).extract().asString());
    }

    @Test
    void umgesetztePfadeEntsprechenDemVertrag() {
        List<String> abweichungen = new ArrayList<>();
        JsonNode pfade = erzeugt.path("paths");
        assertTrue(pfade.size() > 0, "Keine Pfade in der erzeugten OpenAPI");

        for (Iterator<Map.Entry<String, JsonNode>> it = pfade.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> pfad = it.next();
            JsonNode vertragsPfad = vertrag.path("paths").path(pfad.getKey());
            if (vertragsPfad.isMissingNode()) {
                abweichungen.add(pfad.getKey() + ": fehlt im Vertrag");
                continue;
            }
            for (String methode : METHODEN) {
                JsonNode op = pfad.getValue().path(methode);
                if (op.isMissingNode()) {
                    continue;
                }
                String ort = methode.toUpperCase() + " " + pfad.getKey();
                JsonNode vertragsOp = vertragsPfad.path(methode);
                if (vertragsOp.isMissingNode()) {
                    abweichungen.add(ort + ": Methode fehlt im Vertrag");
                    continue;
                }
                vergleicheOperation(ort, op, vertragsOp, abweichungen);
            }
        }
        if (!abweichungen.isEmpty()) {
            fail("Abweichungen zum Vertrag docs/api/openapi.yaml:\n  " + String.join("\n  ", abweichungen));
        }
    }

    private void vergleicheOperation(String ort, JsonNode op, JsonNode vertragsOp, List<String> abweichungen) {
        String id = op.path("operationId").asText();
        String vertragsId = vertragsOp.path("operationId").asText();
        if (!id.equals(vertragsId)) {
            abweichungen.add(ort + ": operationId «" + id + "» statt «" + vertragsId + "»");
        }

        Set<String> codes = feldnamen(op.path("responses"));
        Set<String> vertragsCodes = feldnamen(vertragsOp.path("responses"));
        if (!codes.equals(vertragsCodes)) {
            abweichungen.add(ort + ": Statuscodes " + codes + " statt " + vertragsCodes);
        }

        for (String code : codes) {
            if (!vertragsCodes.contains(code)) {
                continue;
            }
            JsonNode antwort = aufloesen(op.path("responses").path(code), erzeugt);
            JsonNode vertragsAntwort = aufloesen(vertragsOp.path("responses").path(code), vertrag);
            vergleicheInhalt(ort + " → " + code, antwort.path("content"), vertragsAntwort.path("content"), abweichungen);
        }

        JsonNode body = aufloesen(op.path("requestBody"), erzeugt);
        JsonNode vertragsBody = aufloesen(vertragsOp.path("requestBody"), vertrag);
        if (body.isMissingNode() != vertragsBody.isMissingNode()) {
            abweichungen.add(ort + ": Anfrage-Body " + (body.isMissingNode() ? "fehlt" : "ist nicht im Vertrag"));
        } else if (!body.isMissingNode()) {
            vergleicheInhalt(ort + " → Anfrage", body.path("content"), vertragsBody.path("content"), abweichungen);
        }
    }

    private void vergleicheInhalt(String ort, JsonNode inhalt, JsonNode vertragsInhalt, List<String> abweichungen) {
        Set<String> typen = feldnamen(inhalt);
        Set<String> vertragsTypen = feldnamen(vertragsInhalt);
        if (!typen.equals(vertragsTypen)) {
            abweichungen.add(ort + ": Medientypen " + typen + " statt " + vertragsTypen);
            return;
        }
        for (String typ : typen) {
            JsonNode schema = normalisieren(inhalt.path(typ).path("schema"), erzeugt, 0);
            JsonNode vertragsSchema = normalisieren(vertragsInhalt.path(typ).path("schema"), vertrag, 0);
            vergleicheSchema(ort + " (" + typ + ")", schema, vertragsSchema, abweichungen);
        }
    }

    private void vergleicheSchema(String ort, JsonNode schema, JsonNode vertragsSchema, List<String> abweichungen) {
        String typ = schema.path("type").asText("");
        String vertragsTyp = vertragsSchema.path("type").asText("");
        if (!typ.isEmpty() && !vertragsTyp.isEmpty() && !typ.equals(vertragsTyp)) {
            abweichungen.add(ort + ": Typ «" + typ + "» statt «" + vertragsTyp + "»");
            return;
        }
        if (schema.has("enum") && vertragsSchema.has("enum")) {
            Set<String> werte = werte(schema.path("enum"));
            Set<String> vertragsWerte = werte(vertragsSchema.path("enum"));
            if (!werte.equals(vertragsWerte)) {
                abweichungen.add(ort + ": Enum " + werte + " statt " + vertragsWerte);
            }
        }
        if (schema.has("items") || vertragsSchema.has("items")) {
            vergleicheSchema(ort + "[]", schema.path("items"), vertragsSchema.path("items"), abweichungen);
        }
        Set<String> felder = feldnamen(schema.path("properties"));
        Set<String> vertragsFelder = feldnamen(vertragsSchema.path("properties"));
        if (!felder.equals(vertragsFelder)) {
            abweichungen.add(ort + ": Felder " + felder + " statt " + vertragsFelder);
            return;
        }
        for (String feld : felder) {
            vergleicheSchema(ort + "." + feld, schema.path("properties").path(feld),
                    vertragsSchema.path("properties").path(feld), abweichungen);
        }
    }

    /** Löst $ref, allOf und nullable-Varianten auf, damit beide Seiten vergleichbar sind. */
    private JsonNode normalisieren(JsonNode schema, JsonNode wurzel, int tiefe) {
        ObjectNode ergebnis = JsonNodeFactory.instance.objectNode();
        if (schema == null || schema.isMissingNode() || tiefe > MAX_TIEFE) {
            return ergebnis;
        }
        schema = aufloesen(schema, wurzel);

        for (String variante : List.of("oneOf", "anyOf")) {
            if (schema.has(variante)) {
                for (JsonNode teil : schema.path(variante)) {
                    JsonNode n = normalisieren(teil, wurzel, tiefe + 1);
                    if (!"null".equals(n.path("type").asText())) {
                        ergebnis.setAll((ObjectNode) n);
                    }
                }
            }
        }
        if (schema.has("allOf")) {
            for (JsonNode teil : schema.path("allOf")) {
                zusammenfuehren(ergebnis, (ObjectNode) normalisieren(teil, wurzel, tiefe + 1));
            }
        }

        JsonNode typ = schema.path("type");
        if (typ.isArray()) {
            for (JsonNode t : typ) {
                if (!"null".equals(t.asText())) {
                    ergebnis.put("type", t.asText());
                }
            }
        } else if (typ.isTextual()) {
            ergebnis.put("type", typ.asText());
        }
        if (schema.has("enum")) {
            ergebnis.set("enum", schema.path("enum"));
        }
        if (schema.has("items")) {
            ergebnis.set("items", normalisieren(schema.path("items"), wurzel, tiefe + 1));
        }
        if (schema.has("properties")) {
            ObjectNode felder = ergebnis.has("properties") ? (ObjectNode) ergebnis.get("properties") : ergebnis.putObject("properties");
            for (Iterator<Map.Entry<String, JsonNode>> it = schema.path("properties").fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> feld = it.next();
                felder.set(feld.getKey(), normalisieren(feld.getValue(), wurzel, tiefe + 1));
            }
            ergebnis.put("type", "object");
        }
        return ergebnis;
    }

    private static void zusammenfuehren(ObjectNode ziel, ObjectNode teil) {
        if (teil.has("properties")) {
            ObjectNode felder = ziel.has("properties") ? (ObjectNode) ziel.get("properties") : ziel.putObject("properties");
            felder.setAll((ObjectNode) teil.get("properties"));
        }
        teil.fields().forEachRemaining(e -> {
            if (!e.getKey().equals("properties")) {
                ziel.set(e.getKey(), e.getValue());
            }
        });
    }

    private static JsonNode aufloesen(JsonNode knoten, JsonNode wurzel) {
        int schritte = 0;
        while (knoten.has("$ref") && schritte++ < 10) {
            String ref = knoten.path("$ref").asText();
            if (!ref.startsWith("#/")) {
                throw new IllegalStateException("Externe Referenz nicht unterstützt: " + ref);
            }
            knoten = wurzel.at(ref.substring(1));
        }
        return knoten;
    }

    private static Set<String> feldnamen(JsonNode knoten) {
        Set<String> namen = new TreeSet<>();
        knoten.fieldNames().forEachRemaining(namen::add);
        return namen;
    }

    private static Set<String> werte(JsonNode array) {
        Set<String> werte = new TreeSet<>();
        array.forEach(w -> werte.add(w.asText()));
        return werte;
    }
}
