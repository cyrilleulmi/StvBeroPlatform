package ch.stvbero.platform.core.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Ort eines Termins oder einer Serie (TER-04), gespeichert in den Spalten {@code ort_*}. */
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
        Location o = new Location();
        o.name = dto.name().strip();
        o.address = EventRules.blankToNull(dto.address());
        o.mapsUrl = EventRules.blankToNull(dto.mapsUrl());
        o.latitude = dto.latitude();
        o.longitude = dto.longitude();
        return o;
    }

    public LocationDto dto() {
        return name == null ? null : new LocationDto(name, address, mapsUrl, latitude, longitude);
    }
}
