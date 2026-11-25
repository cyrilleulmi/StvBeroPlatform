package ch.stvbero.platform.driver;

import ch.stvbero.platform.core.error.Concurrently;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * F-20: driver list for match days (DRV-01 to DRV-13), against the seed of docs/test-data.md: match day 04 with
 * Petra's full car (Anna, Lea, Mia), Sara's empty car and Jana without a seat. Default "now": Mon 16.11.2026 06:00.
 */
@QuarkusTest
class DriverListTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";
    static final String PETRA = "20000000-0000-4000-8000-000000000002";
    static final String ANNA = "20000000-0000-4000-8000-000000000005";
    static final String LEA = "20000000-0000-4000-8000-000000000006";
    static final String MIA = "20000000-0000-4000-8000-000000000007";
    static final String SARA = "20000000-0000-4000-8000-000000000008";
    static final String NINA = "20000000-0000-4000-8000-000000000009";
    static final String JANA = "20000000-0000-4000-8000-000000000010";
    static final String KIM = "20000000-0000-4000-8000-000000000011";
    static final String EVA = "20000000-0000-4000-8000-000000000012";
    static final String T01 = "40000000-0000-4000-8000-000000000001";
    static final String T04 = "40000000-0000-4000-8000-000000000004";
    static final String T05 = "40000000-0000-4000-8000-000000000005";
    static final String CAR_PETRA = "b0000000-0000-4000-8000-000000000001";
    static final String CAR_SARA = "b0000000-0000-4000-8000-000000000002";
    static final String MAPS_SEEFELD = "https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf";

    @Inject
    RoutingSimulator simulator;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    // ------------------------------------------------------------------ Helpers

    private static ValidatableResponse list(String session, String event) {
        return given().cookie("stv_session", session).get("/api/events/" + event + "/driver-list").then();
    }

    private static ValidatableResponse addCar(String session, String event, String driver, Object seats) {
        String body = driver == null ? "{\"seats\":" + seats + "}"
                : "{\"driverId\":\"" + driver + "\",\"seats\":" + seats + "}";
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/events/" + event + "/driver-list/cars").then();
    }

    private static ValidatableResponse seats(String session, String event, String car, int seats) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body("{\"seats\":" + seats + "}")
                .put("/api/events/" + event + "/driver-list/cars/" + car).then();
    }

    private static ValidatableResponse removeCar(String session, String event, String car) {
        return given().cookie("stv_session", session).delete("/api/events/" + event + "/driver-list/cars/" + car).then();
    }

    private static ValidatableResponse ride(String session, String event, String car, String person) {
        return given().cookie("stv_session", session)
                .put("/api/events/" + event + "/driver-list/cars/" + car + "/passengers/" + person).then();
    }

    private static ValidatableResponse leave(String session, String event, String car, String person) {
        return given().cookie("stv_session", session)
                .delete("/api/events/" + event + "/driver-list/cars/" + car + "/passengers/" + person).then();
    }

    private static ValidatableResponse distance(String session, String event, Object km) {
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body("{\"distanceKm\":" + km + "}")
                .put("/api/events/" + event + "/driver-list/route").then();
    }

    private static ValidatableResponse recalculate(String session, String event) {
        return given().cookie("stv_session", session).post("/api/events/" + event + "/driver-list/route/recalculate").then();
    }

    private static ValidatableResponse summary(String session) {
        return given().cookie("stv_session", session).get("/api/driver-summary").then();
    }

    private static void routing(String body) {
        given().contentType(ContentType.JSON).body(body).put("/api/test/routing").then().statusCode(204);
    }

    private static void clock(String now) {
        given().contentType(ContentType.JSON).body("{\"now\":\"" + now + "\"}").put("/api/test/clock").then().statusCode(204);
    }

    /** Changes meeting point and venue of match day 04 (same date, so the swiss unihockey matches stay). */
    private static void changeEvent04(String session, String meetingPoint, String venue) {
        String body = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"2026-11-22T09:30:00+01:00","end":"2026-11-22T13:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"%s","address":"Musterdorf","mapsUrl":"%s"},
                 "notes":"Weisse Trikots mitnehmen","meetingPoint":{"time":"2026-11-22T08:00:00+01:00","location":"%s"}}"""
                .formatted(TEAM_1, venue, MAPS_SEEFELD, meetingPoint);
        given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .put("/api/events/" + T04).then().statusCode(200);
    }

    private static String createMatchDay(String session, String meetingPoint, String venue) {
        String location = venue == null ? "" : ",\"location\":{\"name\":\"" + venue + "\"}";
        String mp = meetingPoint == null ? ""
                : ",\"meetingPoint\":{\"time\":\"2026-12-06T08:00:00+01:00\",\"location\":\"" + meetingPoint + "\"}";
        String body = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"2026-12-06T10:00:00+01:00","end":"2026-12-06T14:00:00+01:00",
                 "teamIds":["%s"]%s%s}""".formatted(TEAM_1, location, mp);
        return given().cookie("stv_session", session).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    /** Driver-list notifications created after the reset: recipient first name → texts. */
    private static List<Map<String, Object>> notifications() {
        JsonPath json = given().get("/api/test/notifications").then().statusCode(200).extract().jsonPath();
        return json.getList("findAll { it.type == 'DRIVER_LIST_CHANGED' }");
    }

    private static List<String> textsFor(String firstName) {
        return notifications().stream()
                .filter(n -> firstName.equals(((Map<?, ?>) n.get("recipient")).get("firstName")))
                .map(n -> (String) n.get("text")).toList();
    }

    private static List<String> passengers(JsonPath json, String car) {
        return json.getList("cars.find { it.id == '" + car + "' }.passengers.firstName");
    }

    // ------------------------------------------------------------------ Read

    @Test
    void drv13SeedMatchDay04WithTwoCarsOneFullAndJanaWithoutSeat() {
        JsonPath json = list(login("anna"), T04).statusCode(200).extract().jsonPath();
        assertThat(json.getBoolean("ready"), equalTo(true));
        assertThat(json.getList("missing"), empty());
        assertThat(json.getString("route.status"), equalTo("CALCULATED"));
        assertThat(json.getString("route.from"), equalTo("Parkplatz Sporthalle Nord"));
        assertThat(json.getString("route.to"), equalTo("Sporthalle Seefeld"));
        assertThat(json.getDouble("route.distanceKm"), equalTo(23.4));
        assertThat(json.getInt("route.durationMinutes"), equalTo(26));
        assertThat(json.getDouble("route.creditKm"), equalTo(46.8));
        // Cars by driver name: Brun (Sara), Keller (Petra)
        assertThat(json.getList("cars.driver.firstName"), contains("Sara", "Petra"));
        assertThat(json.getList("cars.seats"), contains(3, 3));
        assertThat(json.getList("cars.freeSeats"), contains(3, 0));
        assertThat(passengers(json, CAR_PETRA), contains("Mia", "Lea", "Anna"));
        assertThat(json.getList("cars.creditKm", Double.class), contains(46.8, 46.8));
        // Call-up in draft: «dabei» Anna, Lea, Sara, Jana; only Jana has no seat (DRV-04)
        assertThat(json.getBoolean("callUpPublished"), equalTo(false));
        assertThat(json.getList("withoutSeat.firstName"), contains("Jana"));
        assertThat(json.getBoolean("canJoin"), equalTo(true));
        assertThat(json.getBoolean("canManage"), equalTo(false));
        // Members arrange the cars for everyone of the team (DRV-32)
        assertThat(json.getBoolean("canArrange"), equalTo(true));
        assertThat(json.getList("members"), hasSize(8));
    }

    @Test
    void drv05RouteIsCalculatedOncePerMatchDay() {
        String anna = login("anna");
        list(anna, T04).statusCode(200);
        list(anna, T04).statusCode(200);
        list(login("petra"), T04).statusCode(200);
        assertThat(simulator.calls(), equalTo(1));
    }

    @Test
    void drv02CoachesAndAdminsSeeMembersOthersAreForbidden() {
        JsonPath petra = list(login("petra"), T04).statusCode(200).extract().jsonPath();
        assertThat(petra.getBoolean("canManage"), equalTo(true));
        assertThat(petra.getList("members.firstName"), hasItem("Kim"));
        assertThat(petra.getList("members.firstName"), not(hasItem("Eva")));
        list(login("alex"), T04).statusCode(200);
        // Marc is coach of Team 2: coaches act in every team (E-69)
        assertThat(list(login("marc"), T04).statusCode(200).extract().path("canManage"), equalTo(true));
        // Tina plays only in Team 3, Eva only in Team 2
        list(login("tina"), T04).statusCode(403);
        list(login("eva"), T04).statusCode(403);
        list(login("anna"), "40000000-0000-4000-8000-000000000099").statusCode(404);
    }

    @Test
    void drv01OnlyMatchDays() {
        list(login("anna"), T01).statusCode(409).body("rule", equalTo("DRV-01"));
    }

    @Test
    void drv01WithoutMeetingPointItSaysWhatIsMissing() {
        String petra = login("petra");
        String noMeetingPoint = createMatchDay(petra, null, "Sporthalle Seefeld");
        JsonPath json = list(petra, noMeetingPoint).statusCode(200).extract().jsonPath();
        assertThat(json.getBoolean("ready"), equalTo(false));
        assertThat(json.getList("missing"), contains("MEETING_POINT"));
        assertThat(json.get("route"), nullValue());
        assertThat(json.getBoolean("canJoin"), equalTo(false));
        addCar(petra, noMeetingPoint, null, 3).statusCode(409).body("rule", equalTo("DRV-01"));

        String nothing = createMatchDay(petra, null, null);
        assertThat(list(petra, nothing).statusCode(200).extract().jsonPath().getList("missing"),
                contains("MEETING_POINT", "VENUE"));
        assertThat(simulator.calls(), equalTo(0));
    }

    // ------------------------------------------------------------------ Drivers

    @Test
    void drv02EveryMemberCanDriveAndStopAgain() {
        String kim = login("kim");
        // Kim responded «nicht dabei» and is not called up: she may still drive (assumption)
        JsonPath json = addCar(kim, T04, null, 2).statusCode(200).extract().jsonPath();
        assertThat(json.getList("cars.driver.firstName"), contains("Kim", "Sara", "Petra"));
        addCar(kim, T04, null, 2).statusCode(409).body("rule", equalTo("DRV-02"));
        String car = json.getString("cars.find { it.driver.firstName == 'Kim' }.id");
        seats(kim, T04, car, 4).statusCode(200);
        removeCar(kim, T04, car).statusCode(200);
        assertThat(list(kim, T04).extract().jsonPath().getList("cars.driver.firstName"), contains("Sara", "Petra"));
        // Changes made by the driver herself notify nobody
        assertThat(textsFor("Kim"), empty());
    }

    @Test
    void drv02SeatsFromOneToEight() {
        String kim = login("kim");
        addCar(kim, T04, null, 0).statusCode(400);
        addCar(kim, T04, null, 9).statusCode(400);
        addCar(kim, T04, null, "null").statusCode(400);
    }

    @Test
    void drv32MembersArrangeTheCarsForEveryone() {
        // «Plätze zuweisen»: every member of the team enters and removes drivers for others (DRV-32)
        String anna = login("anna");
        addCar(anna, T04, KIM, 2).statusCode(200);
        assertThat(textsFor("Kim"), contains(
                "Du bist am Spieltag So 22.11. als Fahrerin oder Fahrer eingetragen, mit 2 Plätzen."));
        seats(anna, T04, CAR_SARA, 2).statusCode(200);
        removeCar(anna, T04, CAR_SARA).statusCode(200);
        assertThat(list(anna, T04).extract().jsonPath().getBoolean("canArrange"), equalTo(true));
        // Eva (Team 2) cannot
        addCar(login("eva"), T04, EVA, 2).statusCode(403);
    }

    @Test
    void drv02CoachesAndAdminsEnterOthers() {

        addCar(login("petra"), T04, KIM, 2).statusCode(200);
        assertThat(textsFor("Kim"), contains(
                "Du bist am Spieltag So 22.11. als Fahrerin oder Fahrer eingetragen, mit 2 Plätzen."));
        // Eva is not in Team 1
        addCar(login("alex"), T04, EVA, 2).statusCode(422).body("rule", equalTo("DRV-02"));
        // A passenger who becomes driver leaves her seat
        JsonPath json = addCar(login("petra"), T04, MIA, 1).statusCode(200).extract().jsonPath();
        assertThat(passengers(json, CAR_PETRA), contains("Lea", "Anna"));
        assertThat(textsFor("Mia"), contains(
                "Du bist am Spieltag So 22.11. als Fahrerin oder Fahrer eingetragen, mit 1 Platz."));
    }

    @Test
    void drv03SeatsNeverBelowPassengers() {
        String petra = login("petra");
        seats(petra, T04, CAR_PETRA, 2).statusCode(422).body("rule", equalTo("DRV-03"));
        seats(petra, T04, CAR_PETRA, 4).statusCode(200).body("cars.find { it.id == '" + CAR_PETRA + "' }.freeSeats", equalTo(1));
        seats(petra, T04, CAR_SARA, 5).statusCode(200);
        assertThat(textsFor("Sara"), contains("Dein Auto hat am Spieltag So 22.11. jetzt 5 Plätze."));
    }

    @Test
    void drv11RemovingACarNotifiesItsPassengers() {
        removeCar(login("alex"), T04, CAR_PETRA).statusCode(200)
                .body("withoutSeat.firstName", contains("Lea", "Anna", "Jana"));
        String text = "Petra Keller fährt am Spieltag So 22.11. nicht. Du hast keinen Platz mehr in der Fahrerliste.";
        assertThat(textsFor("Anna"), contains(text));
        assertThat(textsFor("Lea"), contains(text));
        assertThat(textsFor("Mia"), contains(text));
        assertThat(textsFor("Petra"), contains("Dein Auto ist am Spieltag So 22.11. nicht mehr in der Fahrerliste."));
    }

    // ------------------------------------------------------------------ Passengers

    @Test
    void drv03MembersRideAlongAndLeave() {
        String jana = login("jana");
        JsonPath json = ride(jana, T04, CAR_SARA, JANA).statusCode(200).extract().jsonPath();
        assertThat(passengers(json, CAR_SARA), contains("Jana"));
        assertThat(json.getList("withoutSeat"), empty());
        assertThat(textsFor("Sara"), contains("Jana Wyss fährt am Spieltag So 22.11. bei dir mit."));
        assertThat(textsFor("Jana"), empty());
        // Again: no change, no second notification
        ride(jana, T04, CAR_SARA, JANA).statusCode(200);
        assertThat(textsFor("Sara").size(), equalTo(1));

        leave(jana, T04, CAR_SARA, JANA).statusCode(200).body("withoutSeat.firstName", contains("Jana"));
        assertThat(textsFor("Sara"), contains("Jana Wyss fährt am Spieltag So 22.11. bei dir mit.",
                "Jana Wyss fährt am Spieltag So 22.11. nicht mehr bei dir mit."));
        leave(jana, T04, CAR_SARA, JANA).statusCode(404);
    }

    @Test
    void drv03ACarIsNeverOverfull() {
        ride(login("jana"), T04, CAR_PETRA, JANA).statusCode(409).body("rule", equalTo("DRV-03"));
        ride(login("petra"), T04, CAR_PETRA, JANA).statusCode(409);
    }

    @Test
    void drv03APersonSitsInOneCarAndMoves() {
        JsonPath json = ride(login("anna"), T04, CAR_SARA, ANNA).statusCode(200).extract().jsonPath();
        assertThat(passengers(json, CAR_PETRA), contains("Mia", "Lea"));
        assertThat(passengers(json, CAR_SARA), contains("Anna"));
        assertThat(textsFor("Petra"), contains("Anna Muster fährt am Spieltag So 22.11. nicht mehr bei dir mit."));
        assertThat(textsFor("Sara"), contains("Anna Muster fährt am Spieltag So 22.11. bei dir mit."));
    }

    @Test
    void drv03MembersCoachesAndAdminsAssignOthers() {
        // Eva (Team 2) can neither view nor ride along
        ride(login("eva"), T04, CAR_SARA, EVA).statusCode(403);

        ride(login("petra"), T04, CAR_SARA, JANA).statusCode(200);
        assertThat(textsFor("Jana"), contains("Du fährst am Spieltag So 22.11. bei Sara Brun mit."));
        assertThat(textsFor("Sara"), contains("Jana Wyss fährt am Spieltag So 22.11. bei dir mit."));
        // Coach moves Lea from Petra's to Sara's car
        ride(login("alex"), T04, CAR_SARA, LEA).statusCode(200);
        assertThat(textsFor("Lea"), contains("Du fährst am Spieltag So 22.11. bei Sara Brun mit."));
        assertThat(textsFor("Petra"), contains("Lea Graf fährt am Spieltag So 22.11. nicht mehr bei dir mit."));
        leave(login("alex"), T04, CAR_SARA, LEA).statusCode(200);
        assertThat(textsFor("Lea"), hasItem(
                "Du fährst am Spieltag So 22.11. nicht mehr bei Sara Brun mit. Du hast noch keinen anderen Platz."));
        // Not a member, or a driver: no seat
        ride(login("alex"), T04, CAR_SARA, EVA).statusCode(422).body("rule", equalTo("DRV-02"));
        ride(login("alex"), T04, CAR_SARA, PETRA).statusCode(422).body("rule", equalTo("DRV-03"));
        ride(login("alex"), T04, "b0000000-0000-4000-8000-000000000099", JANA).statusCode(404);
        // Every member of the team puts others into cars and takes them out (DRV-32)
        leave(login("anna"), T04, CAR_SARA, JANA).statusCode(200);
        ride(login("anna"), T04, CAR_SARA, NINA).statusCode(200);
        assertThat(textsFor("Nina"), contains("Du fährst am Spieltag So 22.11. bei Sara Brun mit."));
    }

    @Test
    void drv03TwoConcurrentRequestsForTheLastSeat() {
        seats(login("sara"), T04, CAR_SARA, 1).statusCode(200);
        String jana = login("jana");
        String nina = login("nina");
        java.util.concurrent.atomic.AtomicInteger turn = new java.util.concurrent.atomic.AtomicInteger();
        List<Integer> status = Concurrently.statusCodes(2, () -> {
            boolean first = turn.getAndIncrement() == 0;
            return given().cookie("stv_session", first ? jana : nina)
                    .put("/api/events/" + T04 + "/driver-list/cars/" + CAR_SARA + "/passengers/" + (first ? JANA : NINA))
                    .statusCode();
        });
        assertThat(status, contains(200, 409));
        assertThat(passengers(list(jana, T04).extract().jsonPath(), CAR_SARA).size(), equalTo(1));
    }

    @Test
    void drv04PublishedCallUpCountsTheCalledUpPlayers() {
        String petra = login("petra");
        given().cookie("stv_session", petra).post("/api/events/" + T04 + "/call-up/publish").then().statusCode(200);
        // Called up: Anna, Lea, Mia, Sara, Jana; seated except Jana
        JsonPath json = list(petra, T04).extract().jsonPath();
        assertThat(json.getBoolean("callUpPublished"), equalTo(true));
        assertThat(json.getList("withoutSeat.firstName"), contains("Jana"));
        leave(petra, T04, CAR_PETRA, MIA).statusCode(200).body("withoutSeat.firstName", containsInAnyOrder("Mia", "Jana"));
    }

    @Test
    void drv02AfterTheEndOnlyCoachesAndAdminsCorrect() {
        clock("2026-11-22T14:00:00+01:00");
        ride(login("jana"), T04, CAR_SARA, JANA).statusCode(409).body("rule", equalTo("DRV-02"));
        assertThat(list(login("jana"), T04).extract().path("canJoin"), equalTo(false));
        ride(login("petra"), T04, CAR_SARA, JANA).statusCode(200);
    }

    // ------------------------------------------------------------------ Route

    @Test
    void drv07UnavailableRoutingStillWorksAndCoachesEnterKilometres() {
        routing("{\"unavailable\":true}");
        String petra = login("petra");
        changeEvent04(petra, "Bahnhof Beromünster", "Sporthalle Seefeld");
        JsonPath json = list(petra, T04).statusCode(200).extract().jsonPath();
        assertThat(json.getString("route.status"), equalTo("UNAVAILABLE"));
        assertThat(json.get("route.distanceKm"), nullValue());
        assertThat(json.get("cars[0].creditKm"), nullValue());
        // The list works without distance
        ride(login("jana"), T04, CAR_SARA, JANA).statusCode(200);

        distance(login("anna"), T04, 20).statusCode(403);
        distance(petra, T04, 0).statusCode(400);
        json = distance(petra, T04, 21.5).statusCode(200).extract().jsonPath();
        assertThat(json.getString("route.status"), equalTo("MANUAL"));
        assertThat(json.getDouble("route.distanceKm"), equalTo(21.5));
        assertThat(json.get("route.durationMinutes"), nullValue());
        assertThat(json.getDouble("route.creditKm"), equalTo(43.0));
        // Manual value stays, even after the outage is over
        routing("{\"routes\":[{\"from\":\"Bahnhof Beromünster\",\"to\":\"Sporthalle Seefeld\",\"distanceKm\":19.8,\"durationMinutes\":22}]}");
        assertThat(list(petra, T04).extract().path("route.status"), equalTo("MANUAL"));
        recalculate(login("anna"), T04).statusCode(403);
        json = recalculate(petra, T04).statusCode(200).extract().jsonPath();
        assertThat(json.getString("route.status"), equalTo("CALCULATED"));
        assertThat(json.getDouble("route.distanceKm"), equalTo(19.8));
    }

    /** Rework #74: in an archived season the route stays as it is, also on recalculation (SAI-02, DRV-07, DRV-09). */
    @Test
    void drv07ArchivedSeasonRouteCannotBeRecalculated() {
        String petra = login("petra");
        distance(petra, T04, 21.5).statusCode(200);
        String alex = login("alex");
        String season = given().cookie("stv_session", alex).contentType(ContentType.JSON)
                .body("{\"name\":\"2027/28\",\"startDate\":\"2027-07-01\",\"endDate\":\"2028-06-30\"}")
                .post("/api/seasons").then().statusCode(201).extract().path("id");
        given().cookie("stv_session", alex).post("/api/seasons/" + season + "/activate").then().statusCode(204);

        recalculate(petra, T04).statusCode(409);
        recalculate(alex, T04).statusCode(409);
        distance(petra, T04, 30).statusCode(409);
        JsonPath json = list(petra, T04).statusCode(200).extract().jsonPath();
        assertThat(json.getString("route.status"), equalTo("MANUAL"));
        assertThat(json.getDouble("route.distanceKm"), equalTo(21.5));
        assertThat(json.getBoolean("canManage"), equalTo(false));
    }

    @Test
    void drv07UnavailableIsRetriedAfterTenMinutes() {
        routing("{\"unavailable\":true}");
        String anna = login("anna");
        assertThat(list(anna, T04).extract().path("route.status"), equalTo("UNAVAILABLE"));
        routing("{\"unavailable\":false}");
        assertThat(list(anna, T04).extract().path("route.status"), equalTo("UNAVAILABLE"));
        clock("2026-11-16T06:10:00+01:00");
        assertThat(list(anna, T04).extract().path("route.status"), equalTo("CALCULATED"));
    }

    @Test
    void drv07NoRouteFound() {
        String petra = login("petra");
        String id = createMatchDay(petra, "Irgendwo", "Nirgends");
        JsonPath json = list(petra, id).statusCode(200).extract().jsonPath();
        assertThat(json.getString("route.status"), equalTo("NOT_FOUND"));
        assertThat(json.getString("route.from"), equalTo("Irgendwo"));
        assertThat(json.getString("route.to"), equalTo("Nirgends"));
    }

    @Test
    void drv08ChangingTheMeetingPointRecalculatesRouteAndCredits() {
        String petra = login("petra");
        list(petra, T04).statusCode(200);
        routing("{\"routes\":[{\"from\":\"Bahnhof Beromünster\",\"to\":\"Sporthalle Seefeld\",\"distanceKm\":19.8,\"durationMinutes\":22}]}");
        changeEvent04(petra, "Bahnhof Beromünster", "Sporthalle Seefeld");
        JsonPath json = list(petra, T04).extract().jsonPath();
        assertThat(json.getString("route.from"), equalTo("Bahnhof Beromünster"));
        assertThat(json.getDouble("route.distanceKm"), equalTo(19.8));
        assertThat(json.getList("cars.creditKm", Double.class), contains(39.6, 39.6));
        assertThat(summary(petra).extract().jsonPath().getList("rows.kilometres", Double.class), contains(39.6, 39.6));
        assertThat(simulator.calls(), equalTo(2));

        // Another venue: calculated again, no route for this pair
        changeEvent04(petra, "Bahnhof Beromünster", "Turnhalle Weid");
        assertThat(list(petra, T04).extract().path("route.status"), equalTo("NOT_FOUND"));
    }

    // ------------------------------------------------------------------ Cancelled, summary

    @Test
    void drv10CancelledKeepsTheListButCreditsNothing() {
        String petra = login("petra");
        given().cookie("stv_session", petra).contentType(ContentType.JSON).body("{\"reason\":\"Halle gesperrt\"}")
                .post("/api/events/" + T04 + "/cancel").then().statusCode(200);
        JsonPath json = list(petra, T04).statusCode(200).extract().jsonPath();
        assertThat(json.getBoolean("cancelled"), equalTo(true));
        assertThat(json.getList("cars.driver.firstName"), contains("Sara", "Petra"));
        assertThat(json.get("route.creditKm"), nullValue());
        assertThat(json.get("cars[0].creditKm"), nullValue());
        assertThat(json.getBoolean("canManage"), equalTo(false));
        ride(login("jana"), T04, CAR_SARA, JANA).statusCode(409).body("rule", equalTo("DRV-10"));
        addCar(petra, T04, KIM, 2).statusCode(409);
        assertThat(summary(petra).extract().jsonPath().getList("rows"), empty());
    }

    @Test
    void drv09SummaryCoachesSeeEveryoneMembersThemselves() {
        JsonPath petra = summary(login("petra")).statusCode(200).extract().jsonPath();
        assertThat(petra.getString("seasonName"), equalTo("2026/27"));
        assertThat(petra.getBoolean("everyone"), equalTo(true));
        assertThat(petra.getList("rows.person.firstName"), contains("Sara", "Petra"));
        assertThat(petra.getList("rows.trips"), contains(1, 1));
        assertThat(petra.getList("rows.kilometres", Double.class), contains(46.8, 46.8));
        assertThat(petra.getList("rows.tripsWithoutDistance"), contains(0, 0));

        JsonPath sara = summary(login("sara")).statusCode(200).extract().jsonPath();
        assertThat(sara.getBoolean("everyone"), equalTo(false));
        assertThat(sara.getList("rows.person.firstName"), contains("Sara"));
        assertThat(sara.getList("rows.kilometres", Double.class), contains(46.8));

        JsonPath anna = summary(login("anna")).statusCode(200).extract().jsonPath();
        assertThat(anna.getList("rows.person.firstName"), contains("Anna"));
        assertThat(anna.getList("rows.trips"), contains(0));

        given().cookie("stv_session", login("anna")).get("/api/driver-summary?season=60000000-0000-4000-8000-000000000099")
                .then().statusCode(404);
        given().get("/api/driver-summary").then().statusCode(401);
    }

    @Test
    void drv09TripWithoutDistanceCountsWithoutKilometres() {
        String petra = login("petra");
        String id = createMatchDay(petra, "Irgendwo", "Nirgends");
        addCar(petra, id, SARA, 2).statusCode(200);
        JsonPath json = summary(petra).extract().jsonPath();
        assertThat(json.getList("rows.person.firstName"), contains("Sara", "Petra"));
        assertThat(json.getList("rows.trips"), contains(2, 1));
        assertThat(json.getList("rows.kilometres", Double.class), contains(46.8, 46.8));
        assertThat(json.getList("rows.tripsWithoutDistance"), contains(1, 0));
    }

    @Test
    void matchDay05HasAnEmptyListWithTheCalledUpPlayersWithoutSeat() {
        JsonPath json = list(login("marc"), T05).statusCode(200).extract().jsonPath();
        assertThat(json.getDouble("route.distanceKm"), equalTo(12.6));
        assertThat(json.getList("cars"), empty());
        // Published call-up: Petra, Eva, Lara, Ella
        assertThat(json.getList("withoutSeat.firstName"), contains("Ella", "Petra", "Eva", "Lara"));
    }
}
