package ch.stvbero.platform.core.event;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** F-03: events and series (TER-01 to TER-11, RR-06, RR-07). IDs from docs/test-data.md. */
@QuarkusTest
class EventsTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String TEAM_2 = "10000000-0000-4000-8000-000000000002";
    static final String TEAM_3 = "10000000-0000-4000-8000-000000000003";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T02 = "40000000-0000-4000-8000-000000000002";
    static final String T03 = "40000000-0000-4000-8000-000000000003";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String T06 = "40000000-0000-4000-8000-000000000006";
    static final String T07 = "40000000-0000-4000-8000-000000000007";
    static final String T08 = "40000000-0000-4000-8000-000000000008";
    static final String SERIES_1 = "70000000-0000-4000-8000-000000000001";
    static final String SERIES_2 = "70000000-0000-4000-8000-000000000002";

    @Inject
    EntityManager entityManager;

    String petra;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
        petra = login("petra");
    }

    private static ValidatableResponse listEvents(String session, String query) {
        return given().cookie("stv_session", session).get("/api/events" + query).then().statusCode(200);
    }

    private static List<String> eventIds(String session, String query) {
        return listEvents(session, query).extract().jsonPath().getList("id");
    }

    private static String eventBody(String start, String end, String... teams) {
        return """
                {"type":"PRACTICE","title":"Zusatztraining","start":"%s","end":"%s","teamIds":[%s],
                 "location":{"name":"Turnhalle Dorf"},"notes":"Hallenschuhe"}"""
                .formatted(start, end, String.join(",", List.of(teams).stream().map(team -> "\"" + team + "\"").toList()));
    }

    private static String seriesBody(String weekday, String startTime, String startDate, String endDate, String... teams) {
        return """
                {"type":"PRACTICE","title":"Konditionstraining","teamIds":[%s],"weekday":"%s","startTime":"%s",
                 "endTime":"21:00","validFrom":"%s","validUntil":"%s","location":{"name":"Sporthalle Nord"}}"""
                .formatted(String.join(",", List.of(teams).stream().map(team -> "\"" + team + "\"").toList()),
                        weekday, startTime, startDate, endDate);
    }

    // ------------------------------------------------------------------ Criteria 1 and 2

    @Test
    void k1SeedSeriesAndEvents() {
        String anna = login("anna");
        given().cookie("stv_session", anna).get("/api/events/" + T01).then().statusCode(200)
                .body("type", equalTo("PRACTICE"))
                .body("start", equalTo("2026-11-17T18:30:00+01:00"))
                .body("end", equalTo("2026-11-17T20:00:00+01:00"))
                .body("teams.name", contains("Team 1", "Team 2"))
                .body("teams[0].myTeamRole", equalTo("PLAYER"))
                .body("teams[1].myTeamRole", nullValue())
                .body("location.name", equalTo("Sporthalle Nord"))
                .body("seriesId", equalTo(SERIES_1))
                .body("responseDeadlineHours", equalTo(24))
                .body("responseDeadline", equalTo("2026-11-16T18:30:00+01:00"))
                .body("myResponse.status", equalTo("ATTENDING"))
                // 12 different players, Lea counts once (ANM-32); responses from the seed (F-04)
                .body("counts.attending", equalTo(7))
                .body("counts.pending", equalTo(2))
                .body("canEdit", equalTo(false));
        given().cookie("stv_session", anna).get("/api/events/" + T04).then().statusCode(200)
                .body("type", equalTo("MATCH_DAY"))
                .body("meetingPoint.location", equalTo("Parkplatz Sporthalle Nord"))
                .body("meetingPoint.time", equalTo("2026-11-22T08:00:00+01:00"))
                .body("notes", equalTo("Weisse Trikots mitnehmen"))
                .body("location.mapsUrl", equalTo("https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf"))
                // Matches and draft call-up from F-05 on (AUF-23: no call-up for Anna yet)
                .body("matches.size()", equalTo(2))
                .body("myCallUp", equalTo("NO_CALL_UP_YET"));
        given().cookie("stv_session", petra).get("/api/series/" + SERIES_1).then().statusCode(200)
                .body("weekday", equalTo("TUESDAY"))
                .body("startTime", equalTo("18:30"))
                .body("endTime", equalTo("20:00"))
                .body("validFrom", equalTo("2026-08-18"))
                .body("validUntil", equalTo("2027-04-27"))
                .body("teamIds", contains(TEAM_1, TEAM_2))
                .body("teams.name", contains("Team 1", "Team 2"))
                .body("exceptions[0].reason", equalTo("Weihnachtsferien"))
                .body("exceptions[0].startDate", equalTo("2026-12-21"))
                .body("exceptions[0].endDate", equalTo("2027-01-03"));
        // Christmas holidays: no practices on 22.12. and 29.12. (TER-07)
        List<String> starts = listEvents(petra, "?from=2026-12-20&to=2027-01-06&team=" + TEAM_2)
                .extract().jsonPath().getList("start");
        assertThat(starts, contains("2027-01-05T18:30:00+01:00"));
    }

    @Test
    void k2UpcomingEventsOfOwnTeamsInChronologicalOrder() {
        String anna = login("anna");
        List<String> annasEvents = eventIds(anna, "");
        assertThat(annasEvents.subList(0, 4), contains(T01, T02, T04, T03));
        assertThat(annasEvents, hasItems(T01, T02, T03, T04, T06));
        assertThat(annasEvents, not(hasItem(T05)));
        assertThat(annasEvents, not(hasItem(T07)));
        List<String> starts = listEvents(anna, "").extract().jsonPath().getList("start");
        assertThat(starts.stream().map(time -> Instant.from(java.time.OffsetDateTime.parse(time))).toList(),
                equalTo(starts.stream().map(time -> Instant.from(java.time.OffsetDateTime.parse(time))).sorted().toList()));

        String tina = login("tina");
        List<String> tinasEvents = eventIds(tina, "");
        assertThat(tinasEvents, not(hasItem(T01)));
        assertThat(tinasEvents, hasItem(T06));
        listEvents(tina, "").body("[0].start", equalTo("2026-11-18T18:00:00+01:00"))
                .body("teams.id", everyItem(hasItem(TEAM_3)));
        // Alex is in no team: no events
        listEvents(login("alex"), "").body("$", empty());
    }

    /** Test finding F-03: the series showed only the own teams (TER-11). */
    @Test
    void ter11SeriesNamesAllTeamsIncludingOthers() {
        given().cookie("stv_session", login("anna")).get("/api/series/" + SERIES_1).then().statusCode(200)
                .body("teams.id", contains(TEAM_1, TEAM_2))
                .body("teams.name", contains("Team 1", "Team 2"))
                .body("teams[0].myTeamRole", equalTo("PLAYER"))
                .body("teams[1].myTeamRole", nullValue());
        given().cookie("stv_session", login("marc")).get("/api/series/" + SERIES_1).then().statusCode(200)
                .body("teams.name", contains("Team 1", "Team 2"));
    }

    @Test
    void k2OnlyMembersSeeAnEvent() {
        given().cookie("stv_session", login("tina")).get("/api/events/" + T01).then().statusCode(403)
                .contentType("application/problem+json");
        given().cookie("stv_session", login("alex")).get("/api/events/" + T01).then().statusCode(200);
        given().cookie("stv_session", petra).get("/api/events/" + UUID.randomUUID()).then().statusCode(404);
        given().get("/api/events").then().statusCode(401);
        given().cookie("stv_session", login("tina")).get("/api/series/" + SERIES_1).then().statusCode(403);
    }

    // ------------------------------------------------------------------ Criterion 3

    @Test
    void k3EventForTwoTeamsAppearsForMembersOfBoth() {
        String id = given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1, TEAM_2))
                .post("/api/events").then().statusCode(201)
                .body("title", equalTo("Zusatztraining"))
                .body("teams.id", contains(TEAM_1, TEAM_2))
                .body("notes", equalTo("Hallenschuhe"))
                .body("status", equalTo("PLANNED"))
                .body("seriesId", nullValue())
                .body("responseDeadlineHours", equalTo(24))
                .body("canEdit", equalTo(true))
                .extract().path("id");
        assertThat(eventIds(login("anna"), ""), hasItem(id));   // Team 1
        assertThat(eventIds(login("eva"), ""), hasItem(id));    // Team 2
        assertThat(eventIds(login("marc"), ""), hasItem(id));   // Coach Team 2
        assertThat(eventIds(login("tina"), ""), not(hasItem(id)));
        // Marc (coach of Team 2) may edit the shared event (RR-06)
        given().cookie("stv_session", login("marc")).get("/api/events/" + id).then().body("canEdit", equalTo(true));
    }

    /** F-12: coaches create and edit events and series of every team (RR-03, E-69); players never (RR-02). */
    @Test
    void k3CoachesOfAnyTeamAndAdminsCanCreate() {
        String body = eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1);
        // Marc is coach of Team 2 only, but may create an event for Team 1 (F-12)
        given().cookie("stv_session", login("marc")).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201);
        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(403).body("rule", equalTo("RR-07"));
        // Lea is a player in two teams, but coach in none
        given().cookie("stv_session", login("lea")).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_2))
                .post("/api/events").then().statusCode(403);
        // Petra is coach in Team 1 and only a player in Team 2: coach rights everywhere (criterion 7)
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_2))
                .post("/api/events").then().statusCode(201);
        given().cookie("stv_session", login("alex")).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201);
        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + T01).then().statusCode(403);
        given().cookie("stv_session", login("anna")).contentType(ContentType.JSON).body("{\"reason\":\"x\"}")
                .post("/api/events/" + T01 + "/cancel").then().statusCode(403);
        given().cookie("stv_session", login("lea")).contentType(ContentType.JSON)
                .body(seriesBody("MONDAY", "18:00", "2026-11-23", "2026-12-21", TEAM_1))
                .post("/api/series").then().statusCode(403);
        given().cookie("stv_session", login("lea")).contentType(ContentType.JSON)
                .body("{\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-02\",\"reason\":\"x\"}")
                .post("/api/series/" + SERIES_1 + "/exceptions").then().statusCode(403);
        // Ruth (coach of Team 3 only) edits series 01 of Team 1 + 2 and adds an exception (F-12)
        given().cookie("stv_session", login("ruth")).contentType(ContentType.JSON)
                .body(seriesBody("MONDAY", "18:00", "2026-11-23", "2026-12-21", TEAM_1))
                .post("/api/series").then().statusCode(201);
        given().cookie("stv_session", login("ruth")).contentType(ContentType.JSON)
                .body("{\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-02\",\"reason\":\"x\"}")
                .post("/api/series/" + SERIES_1 + "/exceptions").then().statusCode(201);
        given().cookie("stv_session", login("ruth")).get("/api/series/" + SERIES_1).then().statusCode(200);
    }

    @Test
    void k3InputsAreChecked() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T19:30:00+01:00", "2026-11-20T18:00:00+01:00", TEAM_1))
                .post("/api/events").then().statusCode(400).body("fields[0].field", equalTo("end"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-10T18:00:00+01:00", "2026-11-10T19:30:00+01:00", TEAM_1))
                .post("/api/events").then().statusCode(400);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", UUID.randomUUID().toString()))
                .post("/api/events").then().statusCode(400).body("fields[0].field", equalTo("teamIds"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00"))
                .post("/api/events").then().statusCode(400);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1, TEAM_2)
                        .replace("PRACTICE", "MATCH_DAY"))
                .post("/api/events").then().statusCode(400).body("fields[0].field", equalTo("teamIds"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1)
                        .replace("\"name\":\"Turnhalle Dorf\"", "\"name\":\"Halle\",\"mapsUrl\":\"http://x.ch\""))
                .post("/api/events").then().statusCode(400);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(seriesBody("MONDAY", "18:00", "2026-11-23", "2026-11-01", TEAM_1))
                .post("/api/series").then().statusCode(400).body("fields[0].field", equalTo("validUntil"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(seriesBody("MONDAY", "21:30", "2026-11-23", "2026-12-21", TEAM_1))
                .post("/api/series").then().statusCode(400).body("fields[0].field", equalTo("endTime"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(seriesBody("MONDAY", "18 Uhr", "2026-11-23", "2026-12-21", TEAM_1))
                .post("/api/series").then().statusCode(400);
    }

    // ------------------------------------------------------------------ Criterion 4

    @Test
    void k4SeriesGeneratesEventForEveryWeek() {
        String id = given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(seriesBody("MONDAY", "19:30", "2026-11-16", "2026-12-21", TEAM_1))
                .post("/api/series").then().statusCode(201)
                .body("weekday", equalTo("MONDAY"))
                .body("exceptions", empty())
                .body("responseDeadlineHours", equalTo(24))
                .extract().path("id");
        List<String> starts = listEvents(login("anna"), "?to=2027-01-31").extract().jsonPath()
                .getList("findAll { it.seriesId == '" + id + "' }.start");
        assertThat(starts, contains("2026-11-16T19:30:00+01:00", "2026-11-23T19:30:00+01:00",
                "2026-11-30T19:30:00+01:00", "2026-12-07T19:30:00+01:00", "2026-12-14T19:30:00+01:00",
                "2026-12-21T19:30:00+01:00"));
    }

    @Test
    void k4SeriesGeneratesNoPastEvents() {
        String id = given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(seriesBody("MONDAY", "05:00", "2026-11-02", "2026-11-30", TEAM_1).replace("21:00", "06:00"))
                .post("/api/series").then().statusCode(201).extract().path("id");
        List<String> starts = listEvents(petra, "").extract().jsonPath().getList("findAll { it.seriesId == '" + id + "' }.start");
        assertThat(starts, contains("2026-11-23T05:00:00+01:00", "2026-11-30T05:00:00+01:00"));
    }

    // ------------------------------------------------------------------ Criterion 5

    @Test
    void k5ExceptionRemovesEventsWithoutResponses() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"startDate\":\"2026-11-30\",\"endDate\":\"2026-12-06\",\"reason\":\"Hallenreinigung\"}")
                .post("/api/series/" + SERIES_1 + "/exceptions").then().statusCode(201)
                .body("exceptions.reason", contains("Hallenreinigung", "Weihnachtsferien"));
        List<String> starts = listEvents(petra, "?from=2026-11-28&to=2026-12-09&type=PRACTICE").extract().jsonPath()
                .getList("findAll { it.seriesId == '" + SERIES_1 + "' }.start");
        assertThat(starts, contains("2026-12-08T18:30:00+01:00"));
    }

    @Test
    void k5ExceptionCancelsEventsWithResponses() {
        // Event 01 has responses from the seed (F-04), e.g. Anna attending
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"startDate\":\"2026-11-17\",\"endDate\":\"2026-11-19\",\"reason\":\"Hallensperre\"}")
                .post("/api/series/" + SERIES_1 + "/exceptions").then().statusCode(201);
        given().cookie("stv_session", petra).get("/api/events/" + T01).then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("cancellationReason", equalTo("Hallensperre"));
        given().cookie("stv_session", login("anna")).get("/api/events/" + T01).then()
                .body("myResponse.status", equalTo("ATTENDING"))
                .body("counts.attending", equalTo(7));
        // Other series are not affected
        given().cookie("stv_session", petra).get("/api/events/" + T02).then().body("status", equalTo("PLANNED"));
    }

    @Test
    void k5ExceptionEndingBeforeItStartsIsInvalid() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"startDate\":\"2026-12-06\",\"endDate\":\"2026-11-30\",\"reason\":\"x\"}")
                .post("/api/series/" + SERIES_1 + "/exceptions").then().statusCode(400);
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body("{\"startDate\":\"2026-11-30\",\"endDate\":\"2026-12-06\",\"reason\":\"x\"}")
                .post("/api/series/" + UUID.randomUUID() + "/exceptions").then().statusCode(404);
    }

    // ------------------------------------------------------------------ Criterion 6

    @Test
    void k6MoveSingleSeriesEvent() {
        String body = """
                {"type":"PRACTICE","title":"Training","start":"2026-11-20T19:00:00+01:00","end":"2026-11-20T20:30:00+01:00",
                 "teamIds":["%s"],"location":{"name":"Sporthalle Nord"}}""".formatted(TEAM_1);
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + T02).then().statusCode(200)
                .body("start", equalTo("2026-11-20T19:00:00+01:00"))
                .body("rescheduled", equalTo(true))
                .body("seriesId", equalTo(SERIES_2));
        // The series stays unchanged, and so do the other events
        given().cookie("stv_session", petra).get("/api/series/" + SERIES_2).then()
                .body("weekday", equalTo("THURSDAY")).body("startTime", equalTo("19:00"));
        List<String> starts = listEvents(petra, "?from=2026-11-19&to=2026-11-27&team=" + TEAM_1 + "&type=PRACTICE")
                .extract().jsonPath().getList("findAll { it.seriesId == '" + SERIES_2 + "' }.start");
        assertThat(starts, contains("2026-11-20T19:00:00+01:00", "2026-11-26T19:00:00+01:00"));

        // The moved event does not take over a later change of the series (TER-08)
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(seriesBody("THURSDAY", "19:30", "2026-08-20", "2027-04-29", TEAM_1).replace("21:00", "21:00"))
                .put("/api/series/" + SERIES_2).then().statusCode(200).body("startTime", equalTo("19:30"));
        given().cookie("stv_session", petra).get("/api/events/" + T02).then()
                .body("start", equalTo("2026-11-20T19:00:00+01:00"));
        starts = listEvents(petra, "?from=2026-11-19&to=2026-12-04&team=" + TEAM_1 + "&type=PRACTICE")
                .extract().jsonPath().getList("findAll { it.seriesId == '" + SERIES_2 + "' }.start");
        assertThat(starts, contains("2026-11-20T19:00:00+01:00", "2026-11-26T19:30:00+01:00", "2026-12-03T19:30:00+01:00"));
        // Past events stay unchanged
        given().cookie("stv_session", petra).get("/api/events?view=past&team=" + TEAM_1 + "&type=PRACTICE")
                .then().body("findAll { it.seriesId == '" + SERIES_2 + "' }[0].start", equalTo("2026-11-12T19:00:00+01:00"));
    }

    @Test
    void ter08SeriesMovedToOtherWeekday() {
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(seriesBody("FRIDAY", "19:00", "2026-08-20", "2026-12-31", TEAM_1))
                .put("/api/series/" + SERIES_2).then().statusCode(200);
        List<String> starts = listEvents(petra, "?to=2026-12-31&team=" + TEAM_1 + "&type=PRACTICE")
                .extract().jsonPath().getList("findAll { it.seriesId == '" + SERIES_2 + "' }.start");
        // Event 02 has had a response (Anna) since F-04: it is cancelled instead of removed (TER-07, states.md)
        assertThat(starts, contains("2026-11-19T19:00:00+01:00", "2026-11-20T19:00:00+01:00",
                "2026-11-27T19:00:00+01:00", "2026-12-04T19:00:00+01:00", "2026-12-11T19:00:00+01:00",
                "2026-12-18T19:00:00+01:00", "2026-12-25T19:00:00+01:00"));
        given().cookie("stv_session", petra).get("/api/events/" + T02).then().statusCode(200)
                .body("status", equalTo("CANCELLED")).body("cancellationReason", equalTo("Serie geändert"));
        // Without responses an event is removed: Thu 26.11.
        given().cookie("stv_session", petra).get("/api/events/" + UUID.nameUUIDFromBytes("termin-2-2026-11-26".getBytes()))
                .then().statusCode(404);
    }

    // ------------------------------------------------------------------ Criterion 7

    @Test
    void k7CancelledEventWithReason() {
        given().cookie("stv_session", login("anna")).get("/api/events/" + T03).then().statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("cancellationReason", equalTo("Halle besetzt"))
                .body("canEdit", equalTo(false));
        given().cookie("stv_session", petra).get("/api/events/" + T03).then().body("canEdit", equalTo(false));
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"reason\":\"Nochmals\"}")
                .post("/api/events/" + T03 + "/cancel").then().statusCode(409).body("rule", equalTo("TER-06"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(eventBody("2026-11-24T18:30:00+01:00", "2026-11-24T20:00:00+01:00", TEAM_1))
                .put("/api/events/" + T03).then().statusCode(409);
    }

    @Test
    void ter06CancelEvent() {
        // Marc is coach of Team 2 and may cancel the shared event (RR-06)
        given().cookie("stv_session", login("marc")).contentType(ContentType.JSON).body("{\"reason\":\"  Halle gesperrt \"}")
                .post("/api/events/" + T01 + "/cancel").then().statusCode(200)
                .body("status", equalTo("CANCELLED")).body("cancellationReason", equalTo("Halle gesperrt"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"reason\":\"\"}")
                .post("/api/events/" + T02 + "/cancel").then().statusCode(400);
        // Past events cannot be cancelled
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"reason\":\"x\"}")
                .post("/api/events/" + T07 + "/cancel").then().statusCode(409);
    }

    // ------------------------------------------------------------------ Criterion 8

    @Test
    void k8ResponseDeadlinePerEventAndSeries() {
        String body = eventBody("2026-11-20T18:00:00+01:00", "2026-11-20T19:30:00+01:00", TEAM_1)
                .replace("\"notes\"", "\"responseDeadlineHours\":48,\"notes\"");
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201)
                .body("responseDeadlineHours", equalTo(48))
                .body("responseDeadline", equalTo("2026-11-18T18:00:00+01:00"));
        String seriesId = given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(seriesBody("MONDAY", "19:30", "2026-11-16", "2026-11-30", TEAM_1)
                        .replace("\"location\"", "\"responseDeadlineHours\":2,\"location\""))
                .post("/api/series").then().statusCode(201).body("responseDeadlineHours", equalTo(2))
                .extract().path("id");
        listEvents(petra, "?to=2026-11-16").body("find { it.seriesId == '" + seriesId + "' }.responseDeadline",
                equalTo("2026-11-16T17:30:00+01:00"));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body.replace("48", "-1")).post("/api/events").then().statusCode(400)
                .body("fields[0].field", equalTo("responseDeadlineHours"))
                .body("fields[0].message", equalTo("Mindestens 0 Stunden."));
    }

    /** Test finding F-03: Bean Validation returned English messages («must be less than or equal to 336»). */
    @Test
    void ter10ResponseDeadlineAtMostTwoWeeksWithGermanMessage() {
        String body = eventBody("2026-11-21T18:00:00+01:00", "2026-11-21T19:30:00+01:00", TEAM_1)
                .replace("\"notes\"", "\"responseDeadlineHours\":337,\"notes\"");
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(400)
                .body("fields[0].field", equalTo("responseDeadlineHours"))
                .body("fields[0].message", equalTo("Höchstens 336 Stunden (14 Tage)."));
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(seriesBody("MONDAY", "19:30", "2026-11-16", "2026-11-30", TEAM_1)
                        .replace("\"location\"", "\"responseDeadlineHours\":337,\"location\""))
                .put("/api/series/" + SERIES_1).then().statusCode(400)
                .body("fields[0].message", equalTo("Höchstens 336 Stunden (14 Tage)."));
        // Other fields: German default texts instead of «must not be null»
        given().cookie("stv_session", petra).contentType(ContentType.JSON)
                .body(body.replace("\"title\":\"Zusatztraining\",", "").replace("337", "24"))
                .post("/api/events").then().statusCode(400)
                .body("fields[0].field", equalTo("title"))
                .body("fields[0].message", not(containsString("must")));
    }

    // ------------------------------------------------------------------ Criterion 9

    @Test
    void k9FilterByTeamTypeAndPast() {
        // Event 06 also belongs to Team 3; Anna is not a member there but sees it through Team 1
        assertThat(eventIds(login("anna"), "?team=" + TEAM_3), contains(T06));
        // Coaches see all events of a team with the team filter, even without membership (F-12)
        assertThat(eventIds(petra, "?team=" + TEAM_3), hasItem(T06));
        assertThat(eventIds(petra, "?team=" + TEAM_3).size(), greaterThan(1));
        // Without the filter only the own teams (TER-13): no event of series 03 (Team 3 only)
        String series3 = java.util.UUID.nameUUIDFromBytes("termin-3-2026-11-18".getBytes(
                java.nio.charset.StandardCharsets.UTF_8)).toString();
        assertThat(eventIds(petra, "?team=" + TEAM_3), hasItem(series3));
        assertThat(eventIds(petra, ""), not(hasItem(series3)));
        assertThat(eventIds(petra, "?type=MATCH_DAY"), contains(T04, T05));
        assertThat(eventIds(petra, "?type=OTHER&team=" + TEAM_1), contains(T06));
        List<String> past = eventIds(login("anna"), "?view=past");
        assertThat(past.get(1), equalTo(T07));
        assertThat(past, not(hasItem(T08)));
        assertThat(eventIds(login("vera"), "?view=past"), containsInAnyOrder(T08));
        // Past events descending: Thu 12.11. (series 02), Tue 10.11. (event 07), Thu 05.11.
        listEvents(login("anna"), "?view=past").body("[0].start", equalTo("2026-11-12T19:00:00+01:00"))
                .body("[1].start", equalTo("2026-11-10T18:30:00+01:00"))
                .body("[2].start", equalTo("2026-11-05T19:00:00+01:00"));
        given().cookie("stv_session", petra).get("/api/events?view=morgen").then().statusCode(400);
    }
}
