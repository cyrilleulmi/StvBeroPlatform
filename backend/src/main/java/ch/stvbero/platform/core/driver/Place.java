package ch.stvbero.platform.core.driver;

/**
 * A place of a route (DRV-12): the label shown in the app, the text a geocoder can search for, and the
 * coordinates if they are known (from the stored coordinates or from the Google Maps link).
 *
 * @param label     what the app shows, e.g. «Sporthalle Seefeld»
 * @param search    text to search for if there are no coordinates, e.g. «Sporthalle Seefeld, Musterdorf»
 * @param latitude  WGS84, {@code null} if unknown
 * @param longitude WGS84, {@code null} if unknown
 */
public record Place(String label, String search, Double latitude, Double longitude) {

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /** Identifies the place for the stored route: if it changes, the route is calculated again (DRV-08). */
    String key() {
        return label + "|" + search + "|" + (hasCoordinates() ? latitude + "," + longitude : "");
    }
}
