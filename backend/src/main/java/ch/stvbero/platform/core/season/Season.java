package ch.stvbero.platform.core.season;

import ch.stvbero.platform.core.error.DomainError;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Zeitraum, für den Teamzugehörigkeiten und Linien gelten. */
@Entity
@Table(name = "season")
public class Season extends PanacheEntityBase {

    @Id
    public UUID id;

    public String name;

    @Column(name = "start_date")
    public LocalDate startDate;

    @Column(name = "end_date")
    public LocalDate endDate;

    @Column(name = "is_current")
    public boolean current;

    public static Optional<Season> current() {
        return find("current", true).firstResultOptional();
    }

    /** Die aktuelle Saison (SAI-03), sonst 409. */
    public static Season currentOrFail() {
        return current().orElseThrow(() -> DomainError.conflict("Keine aktuelle Saison",
                "Es ist keine aktuelle Saison festgelegt.", "SAI-03"));
    }

    /** Die angegebene Saison, ohne Angabe die aktuelle (Parameter {@code saison}). */
    public static Season orCurrent(UUID id) {
        if (id == null) {
            return currentOrFail();
        }
        return Season.<Season>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diese Saison gibt es nicht."));
    }

    public boolean isArchived() {
        return SeasonRules.isArchived(startDate, current, current().map(a -> a.startDate).orElse(null));
    }

    /** Teams und Zugehörigkeiten dieser Saison dürfen geändert werden, sonst 409 (SAI-02). */
    public void checkWritable() {
        SeasonRules.checkWritable(startDate, current, current().map(a -> a.startDate).orElse(null));
    }
}
