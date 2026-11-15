package ch.stvbero.platform.kern.saison;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.Eindeutig;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.team.Team;
import ch.stvbero.platform.kern.team.Teamzugehoerigkeit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

/** Saisons anlegen, übernehmen und wechseln (SAI-03, SAI-04, SAI-06). */
@ApplicationScoped
public class SaisonService {

    @Inject
    EntityManager em;

    /** Alle Saisons, neueste zuerst. */
    public List<SaisonDto> liste() {
        Saison aktuelle = Saison.aktuelle().orElse(null);
        return Saison.<Saison>list("order by von desc").stream().map(s -> SaisonDto.von(s, aktuelle)).toList();
    }

    @Transactional
    public SaisonDto anlegen(Angemeldet wer, SaisonAnlegen eingabe) {
        wer.pruefeAdmin();
        Saison quelle = eingabe.uebernehmenAus() == null ? null
                : Saison.<Saison>findByIdOptional(eingabe.uebernehmenAus())
                .orElseThrow(() -> FachFehler.nichtGefunden("Die Saison zum Übernehmen gibt es nicht."));

        List<SaisonRegeln.Zeitraum> bestehende = Saison.<Saison>listAll().stream()
                .map(s -> new SaisonRegeln.Zeitraum(s.name, s.von, s.bis)).toList();
        SaisonRegeln.pruefeNeu(new SaisonRegeln.Zeitraum(eingabe.name(), eingabe.von(), eingabe.bis()), bestehende);

        Saison neu = new Saison();
        neu.id = UUID.randomUUID();
        neu.name = eingabe.name().strip();
        neu.von = eingabe.von();
        neu.bis = eingabe.bis();
        neu.aktuell = false;
        neu.persist();
        // Gleichzeitiges Anlegen derselben Saison (Doppelklick): 409 statt 500 (SAI-01).
        Eindeutig.speichern(em, "saison_name_key",
                () -> FachFehler.konflikt("Name vergeben", "Es gibt schon eine Saison «" + neu.name + "».", "SAI-01"));

        if (quelle != null) {
            uebernehmen(quelle, neu);
        }
        return SaisonDto.von(neu, Saison.aktuelle().orElse(null));
    }

    /** Teams und Teamzugehörigkeiten der Vorsaison als Ausgangslage (SAI-04). Linien folgen mit F-02. */
    private void uebernehmen(Saison quelle, Saison ziel) {
        List<Team> teams = Team.list("select t from Team t join t.saisons s where s.id = ?1", quelle.id);
        for (Team team : teams) {
            team.saisons.add(ziel);
        }
        List<Teamzugehoerigkeit> zugehoerigkeiten = Teamzugehoerigkeit.list("saison.id", quelle.id);
        for (Teamzugehoerigkeit z : zugehoerigkeiten) {
            Teamzugehoerigkeit kopie = Teamzugehoerigkeit.neu(z.person, z.team, ziel, z.funktion);
            kopie.persist();
        }
    }

    /** Zur aktuellen Saison machen. Es gibt immer genau eine aktuelle Saison (SAI-03). */
    @Transactional
    public void aktivieren(Angemeldet wer, UUID id) {
        wer.pruefeAdmin();
        Saison saison = Saison.<Saison>findByIdOptional(id)
                .orElseThrow(() -> FachFehler.nichtGefunden("Diese Saison gibt es nicht."));
        if (saison.aktuell) {
            return;
        }
        Saison.update("aktuell = false where aktuell = true");
        em.flush();
        em.refresh(saison);
        saison.aktuell = true;
    }
}
