package ch.stvbero.platform.kern.termin;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Ort eines Termins oder einer Serie (TER-04), gespeichert in den Spalten {@code ort_*}. */
@Embeddable
public class Ort {

    @Column(name = "ort_name")
    public String name;

    @Column(name = "ort_adresse")
    public String adresse;

    @Column(name = "ort_maps_url")
    public String mapsUrl;

    @Column(name = "ort_breite")
    public Double breite;

    @Column(name = "ort_laenge")
    public Double laenge;

    public static Ort von(OrtDto dto) {
        if (dto == null) {
            return null;
        }
        Ort o = new Ort();
        o.name = dto.name().strip();
        o.adresse = TerminRegeln.leerZuNull(dto.adresse());
        o.mapsUrl = TerminRegeln.leerZuNull(dto.mapsUrl());
        o.breite = dto.breite();
        o.laenge = dto.laenge();
        return o;
    }

    public OrtDto dto() {
        return name == null ? null : new OrtDto(name, adresse, mapsUrl, breite, laenge);
    }
}
