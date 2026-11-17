package ch.stvbero.platform.core.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Location of an event or a series (TER-04), stored in the columns {@code location_*}. */
@Embeddable
public class Location {

    @Column(name = "location_name")
    public String name;

    @Column(name = "location_address")
    public String address;

    @Column(name = "location_maps_url")
    public String mapsUrl;

    @Column(name = "location_latitude")
    public Double latitude;

    @Column(name = "location_longitude")
    public Double longitude;

    public static Location of(LocationDto dto) {
        if (dto == null) {
            return null;
        }
        Location location = new Location();
        location.name = dto.name().strip();
        location.address = EventRules.blankToNull(dto.address());
        location.mapsUrl = EventRules.blankToNull(dto.mapsUrl());
        location.latitude = dto.latitude();
        location.longitude = dto.longitude();
        return location;
    }

    public LocationDto dto() {
        return name == null ? null : new LocationDto(name, address, mapsUrl, latitude, longitude);
    }
}
