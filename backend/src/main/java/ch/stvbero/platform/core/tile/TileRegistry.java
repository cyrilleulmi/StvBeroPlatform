package ch.stvbero.platform.core.tile;

import ch.stvbero.platform.core.auth.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

/** Zentrales Register aller Kacheln. Neue Kacheln kommen dazu, ohne dass sich hier etwas ändert (NF-10). */
@ApplicationScoped
public class TileRegistry {

    private final List<TileDefinition> definitionen;

    @Inject
    public TileRegistry(Instance<TileDefinition> all) {
        this(StreamSupport.stream(all.spliterator(), false).toList());
    }

    public TileRegistry(List<TileDefinition> definitionen) {
        this.definitionen = definitionen.stream()
                .sorted(Comparator.comparingInt(TileDefinition::sortOrder).thenComparing(TileDefinition::id))
                .toList();
    }

    /** Kacheln, die die Person sehen darf, in Anzeigereihenfolge (NF-12, RR-05). */
    public List<Tile> visibleFor(CurrentUser person) {
        return definitionen.stream()
                .filter(d -> d.visibleFor(person))
                .map(d -> new Tile(d.id(), d.title(), d.icon(), d.route(), d.color(), d.hint(person)))
                .toList();
    }
}
