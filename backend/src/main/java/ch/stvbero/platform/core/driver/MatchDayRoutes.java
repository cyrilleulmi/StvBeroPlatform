package ch.stvbero.platform.core.driver;

import ch.stvbero.platform.core.event.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

/**
 * Meeting point and venue of a match day as the places of its route (DRV-12), and the stored route that belongs
 * to them. Shared by the driver list, the season plan and the timeline.
 */
@ApplicationScoped
public class MatchDayRoutes {

    @Inject
    EntityManager em;

    @Inject
    Routes routes;

    /** What is missing before a route can be calculated (DRV-01). */
    public static List<DriverRules.Missing> missing(Event event) {
        return DriverRules.missing(event.meetingPointLocation != null && !event.meetingPointLocation.isBlank(),
                event.location != null && event.location.name != null);
    }

    /**
     * Meeting point and venue as places of the route (DRV-12). The meeting point is free text; if it names a venue
     * the app already knows with coordinates (e.g. the home hall from swiss unihockey, «Oberstufenschulhaus
     * Beromünster»), those coordinates are used instead of asking a geocoder (DRV-16). Needs a transaction.
     */
    public Place[] places(Event event) {
        Place meetingPoint = Places.meetingPoint(event.meetingPointLocation);
        Optional<double[]> known = knownVenue(meetingPoint.label());
        if (known.isPresent()) {
            meetingPoint = new Place(meetingPoint.label(), meetingPoint.search(), known.get()[0], known.get()[1]);
        }
        return new Place[] {meetingPoint, Places.venue(event.location.name, event.location.address,
                event.location.mapsUrl, event.location.latitude, event.location.longitude)};
    }

    /** The stored route, if it was calculated for the current meeting point and venue (DRV-08). */
    public Optional<Routes.Stored> current(Event event) {
        if (!missing(event).isEmpty()) {
            return Optional.empty();
        }
        Place[] places = places(event);
        return routes.stored(event.id).filter(s -> s.places().equals(Routes.places(places[0], places[1])));
    }

    /**
     * Coordinates of a venue with this name: «name», «name town» or «name, town», ignoring case. The most recent
     * event wins.
     */
    private Optional<double[]> knownVenue(String text) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        select location_latitude, location_longitude from event
                        where location_latitude is not null and location_longitude is not null
                          and lower(?1) in (lower(location_name),
                                            lower(location_name || ' ' || coalesce(location_address, '')),
                                            lower(location_name || ', ' || coalesce(location_address, '')))
                        order by starts_at desc limit 1""")
                .setParameter(1, text.strip()).getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new double[] {((Number) rows.get(0)[0]).doubleValue(),
                ((Number) rows.get(0)[1]).doubleValue()});
    }
}
