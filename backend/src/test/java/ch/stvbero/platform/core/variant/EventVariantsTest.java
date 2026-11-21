package ch.stvbero.platform.core.variant;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Event variants of a team (VAR-01 to VAR-11) and what they switch (AUF-33), against the seed of
 * docs/test-data.md: the teams of the test data set make a call-up, a new team does not. Event 01 is a practice
 * of Team 1 and Team 2, event 04 a match day of Team 1 from swiss unihockey with a call-up in draft.
 */
@QuarkusTest
class EventVariantsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static ValidatableResponse variants(String session, String team) {
        return given().cookie("stv_session", session).get("/api/teams/" + team + "/event-variants").then();
    }

    private static String standard(String team, VariantKind kind) {
        return VariantRules.standardId(UUID.fromString(team), kind).toString();
    }

    private static ValidatableResponse create(String session, String team, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/teams/" + team + "/event-variants").then();
    }

    private static ValidatableResponse update(String session, String team, String variant, String body) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/teams/" + team + "/event-variants/" + variant).then();
    }

    private static ValidatableResponse event(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event).then();
    }

    private static String createMatchDay(String session, String team, String variantId) {
        String variant = variantId == null ? "" : ",\"variantId\":\"" + variantId + "\"";
        String body = """
                {"type":"MATCH_DAY","title":"Cup","start":"2026-12-06T10:00:00+01:00","end":"2026-12-06T14:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Seefeld"}%s}""".formatted(team, variant);
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    @Test
    void var01EveryTeamHasTheFourStandardVariants() {
        JsonPath json = variants(login("anna"), TEAM_1).statusCode(200).extract().jsonPath();

        assertThat(json.getList("kind"), contains("PRACTICE", "MATCH_DAY", "SU_MATCH_DAY", "OTHER"));
        assertThat(json.getList("name"), contains("Training", "Spieltag", "swiss unihockey Spieltag", "Anlass"));
        assertThat(json.getList("color"), contains("GREEN", "VIOLET", "BLUE", "PINK"));
        assertThat(json.getList("standard"), contains(true, true, true, true));
        assertEquals(standard(TEAM_1, VariantKind.PRACTICE), json.getString("[0].id"));
        // «Anmeldungen» first (VAR-07)
        assertThat(json.getList("[0].sections"), contains("RESPONSES", "ATTENDANCE", "DETAILS"));
        assertThat(json.getList("[2].sections"), contains("RESPONSES", "MATCHES", "CALL_UP", "TIMELINE", "DRIVER_LIST",
                "LINEUPS", "ATTENDANCE", "DETAILS"));
        assertEquals(true, json.getBoolean("[2].autoDeparture"));
        // The teams of the test data set make a call-up (VAR-11)
        assertEquals(true, json.getBoolean("[1].callUp"));
        assertEquals(false, json.getBoolean("[0].callUp"));
    }

    @Test
    void var03EventsCarryTheirVariant() {
        String anna = login("anna");
        // Practice of Team 1 and Team 2: the variant of the team that comes first by name
        event(anna, T01).statusCode(200).body("variant.kind", equalTo("PRACTICE"))
                .body("variant.teamId", equalTo(TEAM_1)).body("variant.color", equalTo("GREEN"));
        // Match day with matches from swiss unihockey
        event(anna, T04).statusCode(200).body("variant.kind", equalTo("SU_MATCH_DAY"))
                .body("variant.name", equalTo("swiss unihockey Spieltag"));
        // A match day entered by hand
        String byHand = createMatchDay(login("petra"), TEAM_1, null);
        event(anna, byHand).body("variant.kind", equalTo("MATCH_DAY")).body("variant.color", equalTo("VIOLET"));
        // The list carries the variant as well (VAR-05)
        given().cookie("stv_session", anna).get("/api/events").then().statusCode(200)
                .body("find { it.id == '" + T04 + "' }.variant.color", equalTo("BLUE"));
    }

    @Test
    void var06WithoutCallUpTheMatchDayWorksWithResponsesAlone() {
        String petra = login("petra");
        String anna = login("anna");
        event(petra, T04).body("callUpStatus", equalTo("DRAFT"));
        event(anna, T04).body("myCallUp", equalTo("NO_CALL_UP_YET"));

        update(petra, TEAM_1, standard(TEAM_1, VariantKind.SU_MATCH_DAY),
                "{\"name\":\"swiss unihockey Spieltag\",\"color\":\"BLUE\",\"callUp\":false}")
                .statusCode(200).body("callUp", equalTo(false));

        event(petra, T04).statusCode(200).body("callUpStatus", nullValue()).body("myCallUp", nullValue())
                .body("variant.callUp", equalTo(false));
        event(anna, T04).body("myCallUp", nullValue());
        given().cookie("stv_session", petra).get("/api/events/" + T04 + "/call-up").then().statusCode(409)
                .body("rule", equalTo("VAR-06"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"personIds\":[]}")
                .put("/api/events/" + T04 + "/call-up").then().statusCode(409);
        given().cookie("stv_session", petra).post("/api/events/" + T04 + "/call-up/publish").then().statusCode(409);

        // Switched on again, the call-up in draft is back (VAR-06)
        update(petra, TEAM_1, standard(TEAM_1, VariantKind.SU_MATCH_DAY),
                "{\"name\":\"swiss unihockey Spieltag\",\"color\":\"BLUE\",\"callUp\":true}").statusCode(200);
        given().cookie("stv_session", petra).get("/api/events/" + T04 + "/call-up").then().statusCode(200)
                .body("calledUpCount", equalTo(5));
    }

    @Test
    void var11ANewTeamMakesNoCallUp() {
        String alex = login("alex");
        String team = given().cookie("stv_session", alex).contentType(ContentType.JSON).body("{\"name\":\"Team 4\"}")
                .post("/api/teams").then().statusCode(201).extract().path("id");

        JsonPath json = variants(alex, team).statusCode(200).extract().jsonPath();
        assertThat(json.getList("kind"), contains("PRACTICE", "MATCH_DAY", "SU_MATCH_DAY", "OTHER"));
        assertThat(json.getList("callUp"), contains(false, false, false, false));

        String matchDay = createMatchDay(alex, team, null);
        event(alex, matchDay).body("callUpStatus", nullValue()).body("variant.callUp", equalTo(false));
        given().cookie("stv_session", alex).get("/api/events/" + matchDay + "/call-up").then().statusCode(409);
    }

    @Test
    void var02OwnVariantIsChosenForAnEventAndFallsBackWhenDeleted() {
        String petra = login("petra");
        String cup = create(petra, TEAM_1, """
                {"name":"Cupspiel","eventType":"MATCH_DAY","color":"ORANGE","callUp":false,
                 "sections":["MATCHES","RESPONSES","DETAILS"]}""")
                .statusCode(201).body("kind", equalTo("CUSTOM")).body("standard", equalTo(false))
                .body("sections", contains("MATCHES", "RESPONSES", "DETAILS")).body("autoDeparture", equalTo(true))
                .body("responseDeadlineHours", equalTo(24)).extract().path("id");
        variants(petra, TEAM_1).body("", hasSize(5)).body("[4].name", equalTo("Cupspiel"));

        String matchDay = createMatchDay(petra, TEAM_1, cup);
        event(petra, matchDay).body("variant.id", equalTo(cup)).body("variant.color", equalTo("ORANGE"))
                .body("variant.sections", contains("MATCHES", "RESPONSES", "DETAILS"))
                .body("callUpStatus", nullValue());

        // Changing the variant changes its events at once (VAR-10)
        update(petra, TEAM_1, cup, "{\"name\":\"Cup\",\"color\":\"RED\",\"callUp\":true}").statusCode(200)
                .body("sections", contains("MATCHES", "RESPONSES", "DETAILS"));
        event(petra, matchDay).body("variant.name", equalTo("Cup")).body("variant.color", equalTo("RED"))
                .body("callUpStatus", equalTo("NONE"));

        given().cookie("stv_session", petra).delete("/api/teams/" + TEAM_1 + "/event-variants/" + cup).then()
                .statusCode(204);
        event(petra, matchDay).body("variant.kind", equalTo("MATCH_DAY")).body("variant.name", equalTo("Spieltag"));
    }

    @Test
    void var03AVariantMustFitTheEvent() {
        String petra = login("petra");
        String template = """
                {"type":"%s","title":"Termin","start":"2026-12-06T10:00:00+01:00","end":"2026-12-06T12:00:00+01:00",
                 "teamIds":["%s"],"variantId":"%s"}""";
        // The practice variant for a match day
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(template.formatted("MATCH_DAY", TEAM_1, standard(TEAM_1, VariantKind.PRACTICE)))
                .post("/api/events").then().statusCode(400).body("fields[0].field", equalTo("variantId"));
        // A variant of another team
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(template.formatted("PRACTICE", TEAM_1, standard(TEAM_3, VariantKind.PRACTICE)))
                .post("/api/events").then().statusCode(400).body("fields[0].field", equalTo("variantId"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(template.formatted("PRACTICE", TEAM_1, UUID.randomUUID()))
                .post("/api/events").then().statusCode(400);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(template.formatted("PRACTICE", TEAM_1, standard(TEAM_1, VariantKind.PRACTICE)))
                .post("/api/events").then().statusCode(201).body("variant.kind", equalTo("PRACTICE"));
    }

    @Test
    void var07SectionsAndNamesAreChecked() {
        String petra = login("petra");
        String practice = standard(TEAM_1, VariantKind.PRACTICE);
        // «Details» cannot be hidden
        update(petra, TEAM_1, practice, "{\"name\":\"Training\",\"color\":\"GREEN\",\"sections\":[\"RESPONSES\"]}")
                .statusCode(400).body("fields[0].field", equalTo("sections"));
        // A practice has no matches
        update(petra, TEAM_1, practice,
                "{\"name\":\"Training\",\"color\":\"GREEN\",\"sections\":[\"MATCHES\",\"DETAILS\"]}").statusCode(400);
        update(petra, TEAM_1, practice,
                "{\"name\":\"Training\",\"color\":\"GREEN\",\"sections\":[\"DETAILS\",\"DETAILS\"]}").statusCode(400);
        update(petra, TEAM_1, practice, "{\"name\":\"\",\"color\":\"GREEN\"}").statusCode(400);
        update(petra, TEAM_1, practice, "{\"name\":\"Training\"}").statusCode(400);
        // Name of another variant of the team
        update(petra, TEAM_1, practice, "{\"name\":\"spieltag\",\"color\":\"GREEN\"}").statusCode(422)
                .body("rule", equalTo("VAR-02"));

        // Renaming, another colour, the order, the defaults: all in one request
        update(petra, TEAM_1, practice, """
                {"name":"Teamtraining","color":"TEAL","sections":["DETAILS","RESPONSES"],
                 "responseDeadlineHours":48,"attendanceCheck":true}""")
                .statusCode(200).body("name", equalTo("Teamtraining")).body("color", equalTo("TEAL"))
                .body("sections", contains("DETAILS", "RESPONSES")).body("responseDeadlineHours", equalTo(48))
                .body("attendanceCheck", equalTo(true)).body("kind", equalTo("PRACTICE"));
        event(login("anna"), T01).body("variant.name", equalTo("Teamtraining"))
                .body("variant.sections", contains("DETAILS", "RESPONSES"));
    }

    @Test
    void var01StandardVariantsStayAndTheNumberIsLimited() {
        String petra = login("petra");
        given().cookie("stv_session", petra)
                .delete("/api/teams/" + TEAM_1 + "/event-variants/" + standard(TEAM_1, VariantKind.OTHER)).then()
                .statusCode(409).body("rule", equalTo("VAR-01"));
        create(petra, TEAM_1, "{\"name\":\"Ohne Art\",\"color\":\"GREY\"}").statusCode(400)
                .body("fields[0].field", equalTo("eventType"));
        for (int i = 5; i <= VariantRules.MAX_PER_TEAM; i++) {
            create(petra, TEAM_1, "{\"name\":\"Variante " + i + "\",\"eventType\":\"OTHER\",\"color\":\"GREY\"}")
                    .statusCode(201);
        }
        create(petra, TEAM_1, "{\"name\":\"Eine zu viel\",\"eventType\":\"OTHER\",\"color\":\"GREY\"}").statusCode(409)
                .body("rule", equalTo("VAR-02"));
    }

    @Test
    void rr07OnlyCoachesAndAdminsChangeVariants() {
        String anna = login("anna");
        String practice = standard(TEAM_1, VariantKind.PRACTICE);
        update(anna, TEAM_1, practice, "{\"name\":\"Training\",\"color\":\"RED\"}").statusCode(403);
        create(anna, TEAM_1, "{\"name\":\"Lager\",\"eventType\":\"OTHER\",\"color\":\"GREY\"}").statusCode(403);
        given().cookie("stv_session", anna).delete("/api/teams/" + TEAM_1 + "/event-variants/" + practice).then()
                .statusCode(403);
        // Anna plays in Team 1 only: she does not see the variants of Team 3
        variants(anna, TEAM_3).statusCode(403);
        // Coaches manage every team (E-69)
        update(login("marc"), TEAM_1, practice, "{\"name\":\"Training\",\"color\":\"RED\"}").statusCode(200);
        variants(login("marc"), TEAM_3).statusCode(200);
        variants(anna, UUID.randomUUID().toString()).statusCode(404);
        update(login("petra"), TEAM_2, practice, "{\"name\":\"Training\",\"color\":\"RED\"}").statusCode(404);
    }
}
