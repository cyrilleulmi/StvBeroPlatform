package ch.stvbero.platform.attendance.event;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Pinning events to the start page (TER-26 to TER-28, E-114). IDs from docs/test-data.md. */
@QuarkusTest
class EventPinsTest {

    static final String T01 = EventsTest.T01;
    static final String T02 = EventsTest.T02;
    static final String T03 = EventsTest.T03;
    static final String T05 = EventsTest.T05;
    static final String T06 = EventsTest.T06;
    static final String T07 = EventsTest.T07;
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String MARC = "20000000-0000-4000-8000-000000000003";

    @Inject
    EntityManager em;

    String petra;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        petra = login("petra");
    }

    private static ValidatableResponse pin(String session, String event, String action) {
        return given().cookie("stv_session", session).post("/api/events/" + event + "/" + action).then();
    }

    private static boolean pinnedInList(String session, String event) {
        JsonPath json = given().cookie("stv_session", session).get("/api/events").then().statusCode(200)
                .extract().jsonPath();
        return json.getBoolean("find { it.id == '" + event + "' }.pinned");
    }

    private Object[] stored(String event) {
        return (Object[]) em.createNativeQuery("select pinned_at, pinned_by from event where id = ?")
                .setParameter(1, UUID.fromString(event)).getSingleResult();
    }

    @Test
    void seedPinsTheTeamDinnerForEveryoneInItsTeams() {
        // Event 06 «Teamessen» (Teams 1 + 2 + 3), pinned by Petra; everyone sees the flag, nothing else is pinned
        for (String person : List.of("anna", "eva", "tina", "petra")) {
            JsonPath json = given().cookie("stv_session", login(person)).get("/api/events").then().statusCode(200)
                    .extract().jsonPath();
            assertThat(person, json.getList("findAll { it.pinned }.id"), contains(T06));
        }
        given().cookie("stv_session", login("anna")).get("/api/events/" + T06).then().statusCode(200)
                .body("pinned", equalTo(true)).body("canEdit", equalTo(false));
        given().cookie("stv_session", login("anna")).get("/api/events/" + T01).then().statusCode(200)
                .body("pinned", equalTo(false));
        Object[] row = stored(T06);
        assertEquals(UUID.fromString(PETRA), row[1]);
        assertEquals(OffsetDateTime.parse("2026-11-15T20:00:00+01:00").toInstant(), toInstant(row[0]));
    }

    private static java.time.Instant toInstant(Object value) {
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        if (value instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        return (java.time.Instant) value;
    }

    @Test
    void coachPinsAndUnpinsAndPlayersSeeIt() {
        pin(petra, T02, "pin").statusCode(200).body("id", equalTo(T02)).body("pinned", equalTo(true));
        Object[] row = stored(T02);
        assertNotNull(row[0]);
        assertEquals(UUID.fromString(PETRA), row[1]);
        String anna = login("anna");
        assertEquals(true, pinnedInList(anna, T02));

        // Pinning twice keeps the first pin
        Object first = row[0];
        pin(petra, T02, "pin").statusCode(200).body("pinned", equalTo(true));
        assertEquals(toInstant(first), toInstant(stored(T02)[0]));

        pin(petra, T02, "unpin").statusCode(200).body("pinned", equalTo(false));
        assertEquals(false, pinnedInList(anna, T02));
        row = stored(T02);
        assertNull(row[0]);
        assertNull(row[1]);
        // Unpinning what is not pinned changes nothing
        pin(petra, T02, "unpin").statusCode(200).body("pinned", equalTo(false));
    }

    @Test
    void coachOfAnyTeamAndAdminMayPin() {
        // Marc coaches Team 2 only; coaches manage every team (E-69), like editing
        pin(login("marc"), T02, "pin").statusCode(200).body("pinned", equalTo(true));
        assertEquals(UUID.fromString(MARC), stored(T02)[1]);
        pin(login("alex"), T02, "unpin").statusCode(200).body("pinned", equalTo(false));
        pin(login("alex"), T05, "pin").statusCode(200).body("pinned", equalTo(true));
    }

    @Test
    void playersMayNotPinNorUnpin() {
        pin(login("anna"), T02, "pin").statusCode(403);
        pin(login("anna"), T06, "unpin").statusCode(403);
        assertEquals(true, pinnedInList(login("anna"), T06));
        // Tina is a player of Team 3 only: also 403, not 404
        pin(login("tina"), T01, "pin").statusCode(403);
    }

    @Test
    void specialPermissionAllowsPinningThatEvent() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"personIds\":[\"" + KIM + "\"]}").put("/api/events/" + T01 + "/grants").then().statusCode(200);
        String kim = login("kim");
        given().cookie("stv_session", kim).get("/api/events/" + T01).then().body("canEdit", equalTo(true));
        pin(kim, T01, "pin").statusCode(200).body("pinned", equalTo(true));
        // Only that event
        pin(kim, T02, "pin").statusCode(403);
    }

    @Test
    void cancelledAndPastEventsCannotBePinned() {
        // Event 03 is cancelled, event 07 is over
        pin(petra, T03, "pin").statusCode(409).body("rule", equalTo("TER-27"))
                .body("detail", equalTo("Ein abgesagter Termin lässt sich nicht anheften."));
        pin(petra, T07, "pin").statusCode(409).body("rule", equalTo("TER-27"))
                .body("detail", equalTo("Ein vergangener Termin lässt sich nicht anheften."));
        pin(petra, T07, "unpin").statusCode(409);
        pin(petra, UUID.randomUUID().toString(), "pin").statusCode(404);
    }

    @Test
    void cancellingKeepsThePin() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"reason\":\"Restaurant zu\"}")
                .post("/api/events/" + T06 + "/cancel").then().statusCode(200).body("pinned", equalTo(true));
        assertEquals(true, pinnedInList(login("anna"), T06));
        pin(petra, T06, "unpin").statusCode(409);
    }

    @Test
    void anEventThatIsOverIsNoLongerPinnable() {
        given().contentType(ContentType.JSON).body("{\"now\":\"2026-12-11T22:30:00+01:00\"}")
                .put("/api/test/clock").then().statusCode(204);
        pin(petra, T06, "unpin").statusCode(409);
        // The past list still carries the flag
        JsonPath json = given().cookie("stv_session", login("anna")).queryParam("view", "past").get("/api/events")
                .then().statusCode(200).extract().jsonPath();
        assertEquals(true, json.getBoolean("find { it.id == '" + T06 + "' }.pinned"));
    }
}
