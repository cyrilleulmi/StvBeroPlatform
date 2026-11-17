package ch.stvbero.platform.core.driver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** F-20, criterion 12 (DRV-12): coordinates of the places from the stored values, the Google Maps link or the address. */
class PlacesTest {

    @Test
    void storedCoordinatesComeFirst() {
        Place venue = Places.venue("Sporthalle Seefeld", "Musterdorf",
                "https://www.google.com/maps/@46.0,7.0,15z", 47.19, 8.35);
        assertEquals(47.19, venue.latitude());
        assertEquals(8.35, venue.longitude());
        assertEquals("Sporthalle Seefeld", venue.label());
    }

    @Test
    void placeMarkerInTheLinkWinsOverTheMapCentre() {
        Place venue = Places.venue("Halle", null,
                "https://www.google.com/maps/place/Halle/@47.10,8.20,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d47.1934!4d8.3541",
                null, null);
        assertEquals(47.1934, venue.latitude());
        assertEquals(8.3541, venue.longitude());
    }

    @Test
    void mapCentreInTheLink() {
        Place venue = Places.venue("Halle", null, "https://www.google.com/maps/@47.2,8.3,15z", null, null);
        assertEquals(47.2, venue.latitude());
        assertEquals(8.3, venue.longitude());
    }

    @Test
    void coordinatesAsSearchParameter() {
        Place venue = Places.venue("Halle", null,
                "https://www.google.com/maps/search/?api=1&query=47.193521%2C8.354127", null, null);
        assertEquals(47.193521, venue.latitude());
        assertEquals(8.354127, venue.longitude());
        Place q = Places.venue("Halle", null, "https://maps.google.com/?q=-33.86,151.21", null, null);
        assertEquals(-33.86, q.latitude());
    }

    @Test
    void searchTextFromTheLink() {
        Place venue = Places.venue("Sporthalle Seefeld", "Musterdorf",
                "https://www.google.com/maps/search/?api=1&query=Sporthalle+Seefeld+Musterdorf", null, null);
        assertFalse(venue.hasCoordinates());
        assertEquals("Sporthalle Seefeld Musterdorf", venue.search());
        Place place = Places.venue("Halle", null, "https://www.google.com/maps/place/Turnhalle+Weid/", null, null);
        assertEquals("Turnhalle Weid", place.search());
    }

    @Test
    void withoutUsableLinkNameAndAddress() {
        Place venue = Places.venue(" Turnhalle Weid ", "Beispielstadt", "https://maps.app.goo.gl/abc123", null, null);
        assertEquals("Turnhalle Weid, Beispielstadt", venue.search());
        assertNull(venue.latitude());
        assertEquals("Turnhalle Weid", Places.venue("Turnhalle Weid", " ", null, null, null).search());
    }

    @Test
    void invalidCoordinatesAreIgnored() {
        Place venue = Places.venue("Halle", "Dorf", "https://www.google.com/maps/@95.0,8.3,15z", 0.0, 0.0);
        assertFalse(venue.hasCoordinates());
        assertEquals("Halle, Dorf", venue.search());
    }

    @Test
    void meetingPointIsSearchedAsItIs() {
        Place meetingPoint = Places.meetingPoint(" Parkplatz Sporthalle Nord ");
        assertEquals("Parkplatz Sporthalle Nord", meetingPoint.label());
        assertEquals("Parkplatz Sporthalle Nord", meetingPoint.search());
        assertFalse(meetingPoint.hasCoordinates());
    }

    @Test
    void anotherPlaceGivesAnotherKey() {
        Place a = Places.venue("Halle", "Dorf", null, null, null);
        Place b = Places.venue("Halle", "Stadt", null, null, null);
        assertFalse(Routes.places(Places.meetingPoint("Bahnhof"), a).equals(Routes.places(Places.meetingPoint("Bahnhof"), b)));
    }
}
