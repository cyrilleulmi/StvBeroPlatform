package ch.stvbero.platform.core.openapi;

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
class OpenApiContractTest {

    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch");
    private static final int MAX_DEPTH = 10;

    JsonNode contract;
    JsonNode generated;

    @BeforeEach
    void load() throws IOException {
        Path file = Path.of("..", "docs", "api", "openapi.yaml");
        contract = new YAMLMapper().readTree(Files.readString(file));
        generated = new ObjectMapper().readTree(given().get("/q/openapi?format=json").then().statusCode(200).extract().asString());
    }

    @Test
    void implementedPathsMatchTheContract() {
        List<String> differences = new ArrayList<>();
        JsonNode paths = generated.path("paths");
        assertTrue(paths.size() > 0, "Keine Pfade in der erzeugten OpenAPI");

        for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> path = it.next();
            JsonNode contractPath = contract.path("paths").path(path.getKey());
            if (contractPath.isMissingNode()) {
                differences.add(path.getKey() + ": fehlt im Vertrag");
                continue;
            }
            for (String method : METHODS) {
                JsonNode op = path.getValue().path(method);
                if (op.isMissingNode()) {
                    continue;
                }
                String location = method.toUpperCase() + " " + path.getKey();
                JsonNode contractOp = contractPath.path(method);
                if (contractOp.isMissingNode()) {
                    differences.add(location + ": Methode fehlt im Vertrag");
                    continue;
                }
                compareOperation(location, op, contractOp, differences);
            }
        }
        if (!differences.isEmpty()) {
            fail("Abweichungen zum Vertrag docs/api/openapi.yaml:\n  " + String.join("\n  ", differences));
        }
    }

    private void compareOperation(String location, JsonNode op, JsonNode contractOp, List<String> differences) {
        String id = op.path("operationId").asText();
        String contractId = contractOp.path("operationId").asText();
        if (!id.equals(contractId)) {
            differences.add(location + ": operationId «" + id + "» statt «" + contractId + "»");
        }

        Set<String> codes = fieldNames(op.path("responses"));
        Set<String> contractCodes = fieldNames(contractOp.path("responses"));
        if (!codes.equals(contractCodes)) {
            differences.add(location + ": Statuscodes " + codes + " statt " + contractCodes);
        }

        for (String code : codes) {
            if (!contractCodes.contains(code)) {
                continue;
            }
            JsonNode response = resolve(op.path("responses").path(code), generated);
            JsonNode contractResponse = resolve(contractOp.path("responses").path(code), contract);
            compareContent(location + " → " + code, response.path("content"), contractResponse.path("content"), differences);
        }

        JsonNode body = resolve(op.path("requestBody"), generated);
        JsonNode contractBody = resolve(contractOp.path("requestBody"), contract);
        if (body.isMissingNode() != contractBody.isMissingNode()) {
            differences.add(location + ": Anfrage-Body " + (body.isMissingNode() ? "missing" : "ist nicht im Vertrag"));
        } else if (!body.isMissingNode()) {
            compareContent(location + " → Anfrage", body.path("content"), contractBody.path("content"), differences);
        }
    }

    private void compareContent(String location, JsonNode inhalt, JsonNode contractContent, List<String> differences) {
        Set<String> types = fieldNames(inhalt);
        Set<String> contractTypes = fieldNames(contractContent);
        if (!types.equals(contractTypes)) {
            differences.add(location + ": Medientypen " + types + " statt " + contractTypes);
            return;
        }
        for (String type : types) {
            JsonNode schema = normalize(inhalt.path(type).path("schema"), generated, 0);
            JsonNode contractSchema = normalize(contractContent.path(type).path("schema"), contract, 0);
            compareSchema(location + " (" + type + ")", schema, contractSchema, differences);
        }
    }

    private void compareSchema(String location, JsonNode schema, JsonNode contractSchema, List<String> differences) {
        String type = schema.path("type").asText("");
        String contractType = contractSchema.path("type").asText("");
        if (!type.isEmpty() && !contractType.isEmpty() && !type.equals(contractType)) {
            differences.add(location + ": Typ «" + type + "» statt «" + contractType + "»");
            return;
        }
        if (schema.has("enum") && contractSchema.has("enum")) {
            Set<String> values = values(schema.path("enum"));
            Set<String> contractValues = values(contractSchema.path("enum"));
            if (!values.equals(contractValues)) {
                differences.add(location + ": Enum " + values + " statt " + contractValues);
            }
        }
        if (schema.has("items") || contractSchema.has("items")) {
            compareSchema(location + "[]", schema.path("items"), contractSchema.path("items"), differences);
        }
        Set<String> fields = fieldNames(schema.path("properties"));
        Set<String> contractFields = fieldNames(contractSchema.path("properties"));
        if (!fields.equals(contractFields)) {
            differences.add(location + ": Felder " + fields + " statt " + contractFields);
            return;
        }
        for (String field : fields) {
            compareSchema(location + "." + field, schema.path("properties").path(field),
                    contractSchema.path("properties").path(field), differences);
        }
    }

    /** Löst $ref, allOf und nullable-Varianten auf, damit beide Seiten vergleichbar sind. */
    private JsonNode normalize(JsonNode schema, JsonNode root, int depth) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if (schema == null || schema.isMissingNode() || depth > MAX_DEPTH) {
            return result;
        }
        schema = resolve(schema, root);

        for (String variant : List.of("oneOf", "anyOf")) {
            if (schema.has(variant)) {
                for (JsonNode part : schema.path(variant)) {
                    JsonNode n = normalize(part, root, depth + 1);
                    if (!"null".equals(n.path("type").asText())) {
                        result.setAll((ObjectNode) n);
                    }
                }
            }
        }
        if (schema.has("allOf")) {
            for (JsonNode part : schema.path("allOf")) {
                merge(result, (ObjectNode) normalize(part, root, depth + 1));
            }
        }

        JsonNode type = schema.path("type");
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (!"null".equals(t.asText())) {
                    result.put("type", t.asText());
                }
            }
        } else if (type.isTextual()) {
            result.put("type", type.asText());
        }
        if (schema.has("enum")) {
            result.set("enum", schema.path("enum"));
        }
        if (schema.has("items")) {
            result.set("items", normalize(schema.path("items"), root, depth + 1));
        }
        if (schema.has("properties")) {
            ObjectNode fields = result.has("properties") ? (ObjectNode) result.get("properties") : result.putObject("properties");
            for (Iterator<Map.Entry<String, JsonNode>> it = schema.path("properties").fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                fields.set(field.getKey(), normalize(field.getValue(), root, depth + 1));
            }
            result.put("type", "object");
        }
        return result;
    }

    private static void merge(ObjectNode target, ObjectNode part) {
        if (part.has("properties")) {
            ObjectNode fields = target.has("properties") ? (ObjectNode) target.get("properties") : target.putObject("properties");
            fields.setAll((ObjectNode) part.get("properties"));
        }
        part.fields().forEachRemaining(e -> {
            if (!e.getKey().equals("properties")) {
                target.set(e.getKey(), e.getValue());
            }
        });
    }

    private static JsonNode resolve(JsonNode nodes, JsonNode root) {
        int steps = 0;
        while (nodes.has("$ref") && steps++ < 10) {
            String ref = nodes.path("$ref").asText();
            if (!ref.startsWith("#/")) {
                throw new IllegalStateException("Externe Referenz nicht unterstützt: " + ref);
            }
            nodes = root.at(ref.substring(1));
        }
        return nodes;
    }

    private static Set<String> fieldNames(JsonNode nodes) {
        Set<String> names = new TreeSet<>();
        nodes.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> values(JsonNode array) {
        Set<String> values = new TreeSet<>();
        array.forEach(w -> values.add(w.asText()));
        return values;
    }
}
