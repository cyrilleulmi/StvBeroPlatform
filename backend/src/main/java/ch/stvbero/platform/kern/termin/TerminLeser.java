package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.person.Person;
import ch.stvbero.platform.kern.person.PersonKurz;
import ch.stvbero.platform.kern.team.Funktion;
import ch.stvbero.platform.kern.team.Team;
import ch.stvbero.platform.kern.team.TeamDto;
import ch.stvbero.platform.kern.team.TeamSaison;
import ch.stvbero.platform.kern.team.Teamzugehoerigkeit;
import ch.stvbero.platform.kern.uhr.Uhr;
import jakarta.persistence.EntityManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Macht aus Terminen die Antworten der API. Lädt Teamnamen, Teamzugehörigkeiten und Antworten für alle
 * Termine auf einmal, damit die Terminliste nicht pro Termin die Datenbank fragt.
 */
final class TerminLeser {

    private record TeamInSaison(UUID team, UUID saison) {
    }

    private record Mitglied(UUID person, Funktion funktion) {
    }

    private record GespeicherteAntwort(AntwortStatus status, String kommentar, Instant geaendertAm,
                                       UUID eingetragenVon, boolean kurzfristig) {
    }

    private final Angemeldet wer;
    private final Map<TeamInSaison, String> namen = new HashMap<>();
    private final Map<TeamInSaison, List<Mitglied>> mitglieder = new HashMap<>();
    private final Map<UUID, Map<UUID, GespeicherteAntwort>> antworten = new HashMap<>();

    TerminLeser(EntityManager em, Angemeldet wer, Collection<Termin> termine) {
        this.wer = wer;
        Set<UUID> saisons = new LinkedHashSet<>();
        termine.forEach(t -> saisons.add(t.saison.id));
        if (!saisons.isEmpty()) {
            TeamSaison.<TeamSaison>list("saison.id in ?1", saisons)
                    .forEach(ts -> namen.put(new TeamInSaison(ts.team.id, ts.saison.id), ts.name));
            Teamzugehoerigkeit.<Teamzugehoerigkeit>list("saison.id in ?1", saisons).forEach(z -> mitglieder
                    .computeIfAbsent(new TeamInSaison(z.team.id, z.saison.id), k -> new ArrayList<>())
                    .add(new Mitglied(z.person.id, z.funktion)));
        }
        List<UUID> ids = termine.stream().map(t -> t.id).toList();
        if (!ids.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Object[]> zeilen = em.createNativeQuery("""
                            select termin_id, person_id, status, kommentar, geaendert_am, eingetragen_von, kurzfristig
                            from antwort where termin_id in (:ids)
                            """)
                    .setParameter("ids", ids).getResultList();
            for (Object[] z : zeilen) {
                antworten.computeIfAbsent((UUID) z[0], k -> new HashMap<>()).put((UUID) z[1],
                        new GespeicherteAntwort(AntwortStatus.valueOf((String) z[2]), (String) z[3], instant(z[4]),
                                (UUID) z[5], Boolean.TRUE.equals(z[6])));
            }
        }
    }

    /** Funktionen der angemeldeten Person in den Teams des Termins (in der Saison des Termins). */
    Set<Funktion> meineFunktionen(Termin t) {
        Set<Funktion> funktionen = new LinkedHashSet<>();
        for (Team team : t.teams) {
            for (Mitglied m : mitglieder.getOrDefault(new TeamInSaison(team.id, t.saison.id), List.of())) {
                if (m.person().equals(wer.personId())) {
                    funktionen.add(m.funktion());
                }
            }
        }
        return funktionen;
    }

    TerminKurzDto kurz(Termin t) {
        return new TerminKurzDto(t.id, t.typ, t.titel, zeit(t.beginn), zeit(t.ende), t.status, t.absagegrund,
                t.verschoben, teams(t), t.ort == null ? null : t.ort.dto(),
                zeit(TerminRegeln.antwortfrist(t.beginn, t.antwortfristStunden)), meineAntwort(t), zaehler(t),
                t.serie == null ? null : t.serie.id);
    }

    TerminDto voll(Termin t, boolean darfBearbeiten) {
        TerminKurzDto k = kurz(t);
        boolean spieltag = t.typ == TerminTyp.SPIELTAG;
        TreffpunktDto treffpunkt = t.treffpunktZeit == null ? null : new TreffpunktDto(zeit(t.treffpunktZeit), t.treffpunktOrt);
        // Spiele und Aufgebot kommen mit F-05.
        MeinAufgebot meinAufgebot = spieltag && meineFunktionen(t).contains(Funktion.SPIELERIN)
                ? MeinAufgebot.NOCH_KEIN_AUFGEBOT : null;
        return new TerminDto(k.id(), k.typ(), k.titel(), k.beginn(), k.ende(), k.status(), k.absagegrund(),
                k.verschoben(), k.teams(), k.ort(), k.antwortfrist(), k.meineAntwort(), k.zaehler(), k.serieId(),
                t.hinweise, t.antwortfristStunden, treffpunkt, spieltag ? List.of() : null,
                spieltag ? AufgebotStatus.KEINES : null, meinAufgebot, darfBearbeiten);
    }

    /** Teams nach Name, mit dem Namen in der Saison des Termins (SAI-02) und der eigenen Funktion. */
    private List<TeamDto> teams(Termin t) {
        List<TeamDto> teams = new ArrayList<>();
        for (Team team : t.teams) {
            TeamInSaison k = new TeamInSaison(team.id, t.saison.id);
            Funktion meine = mitglieder.getOrDefault(k, List.of()).stream()
                    .filter(m -> m.person().equals(wer.personId())).map(Mitglied::funktion).findFirst().orElse(null);
            teams.add(new TeamDto(team.id, namen.getOrDefault(k, ""), team.suTeamId, meine));
        }
        teams.sort(Comparator.comparing(TeamDto::name, String.CASE_INSENSITIVE_ORDER).thenComparing(TeamDto::id));
        return teams;
    }

    private AntwortDto meineAntwort(Termin t) {
        GespeicherteAntwort a = antworten.getOrDefault(t.id, Map.of()).get(wer.personId());
        if (a == null) {
            return AntwortDto.OFFEN;
        }
        PersonKurz von = a.eingetragenVon() == null ? null
                : Person.<Person>findByIdOptional(a.eingetragenVon()).map(PersonKurz::von).orElse(null);
        return new AntwortDto(a.status(), a.kommentar(), zeit(a.geaendertAm()), von, a.kurzfristig());
    }

    /** Gesamtzähler: jede Spielerin eines beteiligten Teams einmal, Coaches zählen nicht (PTF-07). */
    private ZaehlerDto zaehler(Termin t) {
        Set<UUID> spielerinnen = new LinkedHashSet<>();
        for (Team team : t.teams) {
            mitglieder.getOrDefault(new TeamInSaison(team.id, t.saison.id), List.of()).stream()
                    .filter(m -> m.funktion() == Funktion.SPIELERIN).forEach(m -> spielerinnen.add(m.person()));
        }
        Map<UUID, GespeicherteAntwort> zumTermin = antworten.getOrDefault(t.id, Map.of());
        return TerminRegeln.zaehlen(spielerinnen.stream()
                .map(p -> zumTermin.containsKey(p) ? zumTermin.get(p).status() : AntwortStatus.OFFEN).toList());
    }

    static OffsetDateTime zeit(Instant zeitpunkt) {
        return zeitpunkt == null ? null : OffsetDateTime.ofInstant(zeitpunkt, Uhr.ZONE);
    }

    private static Instant instant(Object wert) {
        if (wert instanceof Instant i) {
            return i;
        }
        if (wert instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        if (wert instanceof Timestamp ts) {
            return ts.toInstant();
        }
        return null;
    }
}
