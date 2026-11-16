package ch.stvbero.platform.kern.swissunihockey;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.benachrichtigung.BenachrichtigungArt;
import ch.stvbero.platform.kern.benachrichtigung.Benachrichtigungen;
import ch.stvbero.platform.kern.benachrichtigung.Nachricht;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.saison.Saison;
import ch.stvbero.platform.kern.team.Team;
import ch.stvbero.platform.kern.team.TeamDto;
import ch.stvbero.platform.kern.team.TeamSaison;
import ch.stvbero.platform.kern.termin.Ort;
import ch.stvbero.platform.kern.termin.Quelle;
import ch.stvbero.platform.kern.termin.Spiel;
import ch.stvbero.platform.kern.termin.Termin;
import ch.stvbero.platform.kern.termin.TerminBenachrichtigungen;
import ch.stvbero.platform.kern.termin.TerminRegeln;
import ch.stvbero.platform.kern.termin.TerminStatus;
import ch.stvbero.platform.kern.termin.TerminTyp;
import ch.stvbero.platform.kern.uhr.Uhr;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Abgleich der Spiele eines Teams mit swiss unihockey (SUH-03 bis SUH-10).
 * <ul>
 *   <li>Neue Spiele werden zu Spieltagen: gleicher Tag und gleiche Halle ergeben einen Spieltag (SUH-04).</li>
 *   <li>Geänderte Spiele werden übernommen. Treffpunkt, Hinweise, Antworten und Aufgebot bleiben (SUH-05,
 *       SUH-06); das Team wird benachrichtigt (BEN-06).</li>
 *   <li>Spiele, die swiss unihockey nicht mehr liefert, werden markiert, nicht gelöscht.</li>
 *   <li>Von Hand erfasste Spieltage und Spiele bleiben unberührt (SUH-10).</li>
 *   <li>Ist swiss unihockey nicht erreichbar, ändert sich nichts (502, SUH-09).</li>
 * </ul>
 */
@ApplicationScoped
public class Abgleich {

    private static final Logger LOG = Logger.getLogger(Abgleich.class);

    public static final String NICHT_ERREICHBAR = "swiss unihockey ist im Moment nicht erreichbar. Es wurde nichts "
            + "geändert. Der nächste Abgleich versucht es wieder.";

    @Inject
    SwissUnihockeyQuelle quelle;

    @Inject
    Benachrichtigungen benachrichtigungen;

    @Inject
    EntityManager em;

    @Inject
    Uhr uhr;

    // ------------------------------------------------------------------ Verknüpfen (SUH-02)

    /** Team mit swiss unihockey verknüpfen ({@code null} löst die Verknüpfung). Nur Admins. */
    @Transactional
    public TeamDto verknuepfen(Angemeldet wer, UUID teamId, Integer suTeamId) {
        wer.pruefeAdmin();
        Team team = finden(teamId);
        if (suTeamId != null && suTeamId <= 0) {
            throw FachFehler.ungueltigesFeld("suTeamId", "Die Team-ID ist eine positive Zahl.");
        }
        if (suTeamId != null) {
            Team.<Team>find("suTeamId = ?1 and id <> ?2", suTeamId, team.id).firstResultOptional().ifPresent(t -> {
                throw FachFehler.konflikt("Schon verknüpft", "Die Team-ID " + suTeamId + " ist schon mit «"
                        + name(t) + "» verknüpft.", "SUH-02");
            });
        }
        team.suTeamId = suTeamId;
        return new TeamDto(team.id, name(team), team.suTeamId, null);
    }

    // ------------------------------------------------------------------ Abgleich

    /** Abgleich von Hand auslösen (Admin). */
    @Transactional
    public AbgleichErgebnisDto abgleichen(Angemeldet wer, UUID teamId) {
        wer.pruefeAdmin();
        return abgleichen(teamId);
    }

    /** Ein Team abgleichen, in der aktuellen Saison. Wird auch vom täglichen Lauf verwendet. */
    @Transactional
    public AbgleichErgebnisDto abgleichen(UUID teamId) {
        Team team = finden(teamId);
        Saison saison = Saison.aktuelleOderFehler();
        if (team.suTeamId == null) {
            throw FachFehler.konflikt("Nicht verknüpft",
                    "«" + name(team) + "» ist nicht mit swiss unihockey verknüpft.", "SUH-02");
        }
        if (!team.gehoertZu(saison)) {
            throw FachFehler.konflikt("Nicht in der aktuellen Saison",
                    "«" + name(team) + "» gehört nicht zur Saison " + saison.name + ".", "SUH-03");
        }
        // Zwei Abgleiche desselben Teams laufen nacheinander, nie gleichzeitig.
        em.lock(team, LockModeType.PESSIMISTIC_WRITE);
        List<SuSpiel> geliefert;
        try {
            geliefert = quelle.spiele(team.suTeamId, saison.von.getYear());
        } catch (SuNichtErreichbar e) {
            LOG.warnf("Abgleich swiss unihockey für Team %s (SU %d) fehlgeschlagen: %s", team.id, team.suTeamId,
                    e.getMessage());
            throw new FachFehler(502, "swiss unihockey nicht erreichbar", NICHT_ERREICHBAR, "SUH-09");
        }
        return uebernehmen(team, saison, geliefert);
    }

    /** IDs der verknüpften Teams der aktuellen Saison, für den täglichen Lauf. */
    @Transactional
    public List<UUID> verknuepfteTeams() {
        return em.createQuery("""
                        select ts.team.id from TeamSaison ts
                        where ts.saison.aktuell = true and ts.team.suTeamId is not null
                        order by ts.name, ts.team.id""", UUID.class)
                .getResultList();
    }

    private AbgleichErgebnisDto uebernehmen(Team team, Saison saison, List<SuSpiel> geliefert) {
        Instant jetzt = uhr.jetzt();
        String eigenes = AbgleichRegeln.eigenesTeam(geliefert, team.nameIn(saison));
        Map<Integer, SuSpiel> nachId = new LinkedHashMap<>();
        geliefert.stream().filter(s -> AbgleichRegeln.inSaison(s.anspielzeit(), saison.von, saison.bis))
                .forEach(s -> nachId.put(s.suSpielId(), s));

        List<Spiel> bisher = em.createQuery("""
                        select s from Spiel s join s.termin t join t.teams tm
                        where s.quelle = :quelle and t.saison.id = :saison and tm.id = :team
                        order by s.anspielzeit, s.id""", Spiel.class)
                .setParameter("quelle", Quelle.SWISS_UNIHOCKEY).setParameter("saison", saison.id)
                .setParameter("team", team.id).getResultList();
        Map<Integer, Spiel> bestehend = new LinkedHashMap<>();
        bisher.forEach(s -> bestehend.put(s.suSpielId, s));
        Set<Integer> beiAnderenTeams = beiAnderenTeams(nachId.keySet(), bestehend.keySet());

        // Bestehende importierte Spieltage nach Tag und Halle, damit neue Spiele dazukommen (SUH-04)
        Map<AbgleichRegeln.Schluessel, Termin> spieltage = new LinkedHashMap<>();
        bisher.forEach(s -> spieltage.putIfAbsent(schluessel(s.termin), s.termin));

        Zaehler z = new Zaehler();
        Set<Termin> neuAngelegt = new HashSet<>();
        Set<Termin> zeitraumNeu = new LinkedHashSet<>();
        Map<Termin, List<String>> meldungen = new LinkedHashMap<>();
        Map<Termin, Instant> alterBeginn = new LinkedHashMap<>();
        List<SuSpiel> neue = new ArrayList<>();

        for (SuSpiel g : nachId.values()) {
            if (beiAnderenTeams.contains(g.suSpielId())) {
                // Spiel gehört schon zu einem Spieltag eines anderen Teams (z.B. Derby): dort bleibt es.
                continue;
            }
            Spiel s = bestehend.remove(g.suSpielId());
            if (s == null) {
                neue.add(g);
                continue;
            }
            Termin t = s.termin;
            AbgleichRegeln.Seite seite = AbgleichRegeln.seite(g, eigenes);
            AbgleichRegeln.Stand alt = new AbgleichRegeln.Stand(s.gegner, s.anspielzeit, s.heim,
                    t.ort == null ? null : t.ort.name);
            AbgleichRegeln.Stand neu = new AbgleichRegeln.Stand(seite.gegner(), g.anspielzeit(), seite.heim(),
                    g.halle());
            boolean wesentlich = AbgleichRegeln.wesentlichGeaendert(alt, neu);
            // Das Resultat übernimmt der Abgleich nur, wenn swiss unihockey eines liefert (SUH-08, O-47)
            boolean resultat = g.resultat() != null && !g.resultat().equals(s.resultat);
            if (!wesentlich && !resultat && !s.beiSuNichtMehrVorhanden) {
                z.unveraendert++;
                continue;
            }
            z.geaendert++;
            if (wesentlich) {
                if (t.status == TerminStatus.GEPLANT && !t.istVorbei(jetzt)) {
                    alterBeginn.putIfAbsent(t, t.beginn);
                    meldungen.computeIfAbsent(t, k -> new ArrayList<>()).add(AbgleichRegeln.aenderung(alt, neu));
                }
                AbgleichRegeln.Schluessel ziel = AbgleichRegeln.Schluessel.von(g.anspielzeit(), g.halle());
                if (!ziel.equals(schluessel(t))) {
                    if (Spiel.count("termin.id", t.id) == 1) {
                        // Einziges Spiel: Der Spieltag zieht mit und behält seine eigenen Daten (SUH-06)
                        spieltage.remove(schluessel(t), t);
                        ortSetzen(t, g);
                        spieltage.putIfAbsent(ziel, t);
                    } else {
                        Termin anderer = spieltage.get(ziel);
                        if (anderer == null) {
                            anderer = neuerSpieltag(team, saison, g);
                            neuAngelegt.add(anderer);
                            spieltage.put(ziel, anderer);
                        }
                        s.termin = anderer;
                        zeitraumNeu.add(anderer);
                    }
                }
                zeitraumNeu.add(t);
            }
            s.gegner = neu.gegner();
            s.anspielzeit = neu.anspielzeit();
            s.heim = neu.heim();
            if (resultat) {
                s.resultat = g.resultat();
            }
            s.beiSuNichtMehrVorhanden = false;
            z.betroffen.add(t.id);
            z.betroffen.add(s.termin.id);
        }

        // Nicht mehr geliefert: markieren, nicht löschen. Die Coach entscheidet (zustaende.md).
        for (Spiel s : bestehend.values()) {
            z.nichtMehrVorhanden++;
            if (!s.beiSuNichtMehrVorhanden) {
                s.beiSuNichtMehrVorhanden = true;
                z.betroffen.add(s.termin.id);
            }
        }

        // Neue Spiele: zu einem bestehenden Spieltag am selben Tag in derselben Halle, sonst ein neuer (SUH-04)
        AbgleichRegeln.gruppieren(neue).forEach((schluessel, gruppe) -> {
            Termin t = spieltage.get(schluessel);
            if (t == null) {
                t = neuerSpieltag(team, saison, gruppe.get(0));
                neuAngelegt.add(t);
                spieltage.put(schluessel, t);
            }
            for (SuSpiel g : gruppe) {
                AbgleichRegeln.Seite seite = AbgleichRegeln.seite(g, eigenes);
                Spiel s = Spiel.neu(t);
                s.quelle = Quelle.SWISS_UNIHOCKEY;
                s.suSpielId = g.suSpielId();
                s.gegner = seite.gegner();
                s.heim = seite.heim();
                s.anspielzeit = g.anspielzeit();
                s.resultat = g.resultat();
                s.persist();
                z.neu++;
            }
            zeitraumNeu.add(t);
            z.betroffen.add(t.id);
        });

        // Beginn und Ende der betroffenen Spieltage aus ihren Spielen
        for (Termin t : zeitraumNeu) {
            List<Instant> zeiten = Spiel.von(t.id).stream().map(s -> s.anspielzeit).toList();
            if (zeiten.isEmpty()) {
                continue;
            }
            AbgleichRegeln.Zeitraum zr = AbgleichRegeln.zeitraum(zeiten);
            if (!neuAngelegt.contains(t)) {
                t.verschoben = TerminRegeln.verschoben(t.verschoben, t.beginn, zr.beginn());
            }
            t.beginn = zr.beginn();
            t.ende = zr.ende();
        }

        // Team über geänderte Spiele benachrichtigen (BEN-06)
        List<Nachricht> nachrichten = new ArrayList<>();
        meldungen.forEach((t, texte) -> {
            String text = AbgleichRegeln.textSpielGeaendert(t.titel, alterBeginn.get(t), texte);
            TerminBenachrichtigungen.mitglieder(t)
                    .forEach(p -> nachrichten.add(new Nachricht(BenachrichtigungArt.SPIEL_GEAENDERT, p, t.id, text)));
        });
        benachrichtigungen.senden(nachrichten);

        LOG.infof("Abgleich swiss unihockey Team %s: %d neu, %d geändert, %d nicht mehr vorhanden, %d unverändert",
                team.id, z.neu, z.geaendert, z.nichtMehrVorhanden, z.unveraendert);
        return new AbgleichErgebnisDto(z.neu, z.geaendert, z.nichtMehrVorhanden, z.unveraendert,
                List.copyOf(z.betroffen));
    }

    private static final class Zaehler {
        int neu;
        int geaendert;
        int nichtMehrVorhanden;
        int unveraendert;
        final Set<UUID> betroffen = new LinkedHashSet<>();
    }

    private Set<Integer> beiAnderenTeams(Set<Integer> geliefert, Set<Integer> eigene) {
        List<Integer> unbekannt = geliefert.stream().filter(id -> !eigene.contains(id)).toList();
        if (unbekannt.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(em.createQuery("select s.suSpielId from Spiel s where s.suSpielId in :ids", Integer.class)
                .setParameter("ids", unbekannt).getResultList());
    }

    private static AbgleichRegeln.Schluessel schluessel(Termin t) {
        return AbgleichRegeln.Schluessel.von(t.beginn, t.ort == null ? null : t.ort.name);
    }

    private static Termin neuerSpieltag(Team team, Saison saison, SuSpiel erstes) {
        Termin t = Termin.neu();
        t.saison = saison;
        t.typ = TerminTyp.SPIELTAG;
        t.titel = AbgleichRegeln.TITEL;
        t.beginn = erstes.anspielzeit();
        t.ende = erstes.anspielzeit().plus(AbgleichRegeln.SPIELDAUER);
        ortSetzen(t, erstes);
        t.teams.add(team);
        t.persist();
        return t;
    }

    /** Spielort aus swiss unihockey, mit Google-Maps-Link (SUH-07). */
    private static void ortSetzen(Termin t, SuSpiel g) {
        Ort o = new Ort();
        o.name = AbgleichRegeln.kuerzen(g.halle(), 120);
        o.adresse = g.ort() == null ? null : AbgleichRegeln.kuerzen(g.ort(), 200);
        o.mapsUrl = AbgleichRegeln.mapsUrl(g);
        o.breite = g.breite();
        o.laenge = g.laenge();
        t.ort = o;
    }

    private static Team finden(UUID id) {
        return Team.<Team>findByIdOptional(id)
                .orElseThrow(() -> FachFehler.nichtGefunden("Dieses Team gibt es nicht."));
    }

    /** Name in der aktuellen Saison, sonst der zuletzt verwendete. */
    private static String name(Team team) {
        return Saison.aktuelle().flatMap(team::in).map(ts -> ts.name)
                .orElseGet(() -> team.saisons.stream().max(Comparator.comparing((TeamSaison ts) -> ts.saison.von))
                        .map(ts -> ts.name).orElse(""));
    }
}
