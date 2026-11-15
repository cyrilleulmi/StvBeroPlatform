package ch.stvbero.platform.kern.saison;

import ch.stvbero.platform.kern.fehler.FachFehler;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Zeitraum, für den Teamzugehörigkeiten und Linien gelten. */
@Entity
@Table(name = "saison")
public class Saison extends PanacheEntityBase {

    @Id
    public UUID id;

    public String name;

    public LocalDate von;

    public LocalDate bis;

    public boolean aktuell;

    public static Optional<Saison> aktuelle() {
        return find("aktuell", true).firstResultOptional();
    }

    /** Die aktuelle Saison (SAI-03), sonst 409. */
    public static Saison aktuelleOderFehler() {
        return aktuelle().orElseThrow(() -> FachFehler.konflikt("Keine aktuelle Saison",
                "Es ist keine aktuelle Saison festgelegt.", "SAI-03"));
    }

    /** Die angegebene Saison, ohne Angabe die aktuelle (Parameter {@code saison}). */
    public static Saison oderAktuelle(UUID id) {
        if (id == null) {
            return aktuelleOderFehler();
        }
        return Saison.<Saison>findByIdOptional(id)
                .orElseThrow(() -> FachFehler.nichtGefunden("Diese Saison gibt es nicht."));
    }

    public boolean istArchiv() {
        return SaisonRegeln.istArchiv(von, aktuell, aktuelle().map(a -> a.von).orElse(null));
    }

    /** Teams und Zugehörigkeiten dieser Saison dürfen geändert werden, sonst 409 (SAI-02). */
    public void pruefeBeschreibbar() {
        SaisonRegeln.pruefeBeschreibbar(von, aktuell, aktuelle().map(a -> a.von).orElse(null));
    }
}
