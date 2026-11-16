package ch.stvbero.platform.kern.swissunihockey;

import ch.stvbero.platform.kern.termin.TerminRegeln;
import ch.stvbero.platform.kern.uhr.Uhr;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Die echte Schnittstelle von swiss unihockey (Profil {@code prod}). In {@code dev} und {@code test} ersetzt sie
 * der {@link SuSimulator}; dort existiert diese Bean nicht. Adresse: {@code stv.swiss-unihockey.url}.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "stv.testhilfen", stringValue = "true")
public class SuApiQuelle implements SwissUnihockeyQuelle {

    @ConfigProperty(name = "stv.swiss-unihockey.url")
    URI url;

    @ConfigProperty(name = "stv.swiss-unihockey.zeitlimit", defaultValue = "PT20S")
    Duration zeitlimit;

    @Inject
    Uhr uhr;

    private SuApi api;

    @PostConstruct
    void starten() {
        api = new SuApi(url, zeitlimit);
    }

    @Override
    public List<SuSpiel> spiele(int suTeamId, int saison) {
        return api.spiele(suTeamId, saison, TerminRegeln.datum(uhr.jetzt()));
    }
}
