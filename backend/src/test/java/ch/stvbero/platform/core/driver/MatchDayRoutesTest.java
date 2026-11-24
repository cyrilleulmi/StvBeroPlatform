package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.event.Event;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static ch.stvbero.platform.core.auth.AuthTest.login;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DRV-16: a meeting point that names a venue the app already knows with coordinates (e.g. the home hall from
 * swiss unihockey) uses those coordinates, so that no geocoder has to guess where the hall is.
 */
@QuarkusTest
class MatchDayRoutesTest {

    static final String TEAM_1 = "10000000-0000-4000-8000-000000000001";

    @Inject
    MatchDayRoutes matchDayRoutes;

    @BeforeEach
    void reset() {
        given().post("/api/test/reset").then().statusCode(204);
    }

    private static String createMatchDay(String day, String venue, String town, String coordinates,
                                         String meetingPoint) {
        String body = """
                {"type":"MATCH_DAY","title":"Spieltag","start":"%sT10:00:00+01:00","end":"%sT14:00:00+01:00",
                 "teamIds":["%s"],"location":{"name":"%s","address":"%s"%s},
                 "meetingPoint":{"time":null,"location":"%s"}}"""
                .formatted(day, day, TEAM_1, venue, town, coordinates, meetingPoint);
        return given().cookie("stv_session", login("petra")).contentType(ContentType.JSON).body(body)
                .post("/api/events").then().statusCode(201).extract().path("id");
    }

    private Place[] places(String eventId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> matchDayRoutes.places(Event.findById(UUID.fromString(eventId))));
    }

    @Test
    void meetingPointThatNamesAKnownVenueUsesItsCoordinates() {
        // A home match day delivers the coordinates of the home hall
        createMatchDay("2026-12-06", "Oberstufenschulhaus", "Beromünster",
                ",\"latitude\":47.20666833,\"longitude\":8.188356", "Bahnhof");

        // «name town», «name, town» and «name» all name the hall; case does not matter
        for (String meetingPoint : new String[] {"Oberstufenschulhaus Beromünster", "oberstufenschulhaus, beromünster",
                "Oberstufenschulhaus"}) {
            Place from = places(createMatchDay("2026-12-13", "Sporthalle Seefeld", "Musterdorf", "", meetingPoint))[0];
            assertTrue(from.hasCoordinates(), meetingPoint);
            assertEquals(47.20666833, from.latitude(), 1e-9);
            assertEquals(8.188356, from.longitude(), 1e-9);
            assertEquals(meetingPoint, from.label(), "the label stays what the coach typed");
        }
    }

    @Test
    void unknownMeetingPointIsLeftToTheGeocoder() {
        Place[] places = places(createMatchDay("2026-12-13", "Sporthalle Seefeld", "Musterdorf", "",
                "Parkplatz Sporthalle Nord"));

        assertFalse(places[0].hasCoordinates());
        assertEquals("Parkplatz Sporthalle Nord", places[0].search());
        assertEquals("Sporthalle Seefeld", places[1].label());
    }
}
