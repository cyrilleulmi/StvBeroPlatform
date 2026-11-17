package ch.stvbero.platform.core.tile;

import ch.stvbero.platform.core.auth.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

/** Central registry of all tiles. New tiles are added without any change here (NF-10). */
@ApplicationScoped
public class TileRegistry {

    private final List<TileDefinition> definitions;

    @Inject
    public TileRegistry(Instance<TileDefinition> all) {
        this(StreamSupport.stream(all.spliterator(), false).toList());
    }

    public TileRegistry(List<TileDefinition> definitions) {
        this.definitions = definitions.stream()
                .sorted(Comparator.comparingInt(TileDefinition::sortOrder).thenComparing(TileDefinition::id))
                .toList();
    }

    /** Tiles the user may see, in display order (NF-12, RR-05). */
    public List<Tile> visibleFor(CurrentUser user) {
        return definitions.stream()
                .filter(d -> d.visibleFor(user))
                .map(d -> new Tile(d.id(), d.title(), d.icon(), d.route(), d.color(), d.hint(user)))
                .toList();
    }
}
