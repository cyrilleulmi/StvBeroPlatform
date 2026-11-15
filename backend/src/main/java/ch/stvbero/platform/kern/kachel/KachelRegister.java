package ch.stvbero.platform.kern.kachel;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

/** Zentrales Register aller Kacheln. Neue Kacheln kommen dazu, ohne dass sich hier etwas ändert (NF-10). */
@ApplicationScoped
public class KachelRegister {

    private final List<KachelDefinition> definitionen;

    @Inject
    public KachelRegister(Instance<KachelDefinition> alle) {
        this(StreamSupport.stream(alle.spliterator(), false).toList());
    }

    public KachelRegister(List<KachelDefinition> definitionen) {
        this.definitionen = definitionen.stream()
                .sorted(Comparator.comparingInt(KachelDefinition::reihenfolge).thenComparing(KachelDefinition::id))
                .toList();
    }

    /** Kacheln, die die Person sehen darf, in Anzeigereihenfolge (NF-12, RR-05). */
    public List<Kachel> sichtbarFuer(Angemeldet person) {
        return definitionen.stream()
                .filter(d -> d.sichtbarFuer(person))
                .map(d -> new Kachel(d.id(), d.titel(), d.icon(), d.route(), d.farbe(), d.hinweis(person)))
                .toList();
    }
}
