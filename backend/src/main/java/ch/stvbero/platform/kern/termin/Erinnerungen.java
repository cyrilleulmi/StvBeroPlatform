package ch.stvbero.platform.kern.termin;

import ch.stvbero.platform.kern.benachrichtigung.BenachrichtigungArt;
import ch.stvbero.platform.kern.benachrichtigung.Benachrichtigungen;
import ch.stvbero.platform.kern.benachrichtigung.Nachricht;
import ch.stvbero.platform.kern.uhr.Uhr;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Erinnerung vor der Antwortfrist (BEN-01): an alle Mitglieder der beteiligten Teams, die noch nicht
 * geantwortet haben. Pro Person und Termin höchstens einmal (BEN-13), festgehalten in der Tabelle
 * {@code erinnerung}; zwei gleichzeitige Prüfungen erinnern deshalb nicht doppelt.
 * <p>
 * Läuft jede Minute. Wird die Uhr in dev/test gesetzt, prüft die Test-Hilfe sofort.
 */
@ApplicationScoped
public class Erinnerungen {

    @Inject
    EntityManager em;

    @Inject
    Uhr uhr;

    @Inject
    AntwortService antworten;

    @Inject
    Benachrichtigungen benachrichtigungen;

    @Scheduled(every = "60s", delayed = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void regelmaessig() {
        pruefen();
    }

    /** Fällige Erinnerungen versenden. Gibt die Anzahl der neu versandten zurück. */
    @Transactional
    public int pruefen() {
        Instant jetzt = uhr.jetzt();
        // Grobe Vorauswahl: Beginn nach «jetzt», höchstens längste Frist plus 12 Stunden entfernt.
        Instant bis = jetzt.plus(Duration.ofHours(TerminRegeln.ANTWORTFRIST_HOECHSTENS))
                .plus(BenachrichtigungsRegeln.ERINNERUNG_VOR_FRIST);
        List<Termin> termine = em.createQuery("""
                        select distinct t from Termin t left join fetch t.teams join fetch t.saison
                        where t.status = :status and t.beginn > :jetzt and t.beginn <= :bis
                        order by t.beginn, t.id""", Termin.class)
                .setParameter("status", TerminStatus.GEPLANT).setParameter("jetzt", jetzt).setParameter("bis", bis)
                .getResultList();
        List<Nachricht> nachrichten = new ArrayList<>();
        for (Termin t : termine) {
            if (!BenachrichtigungsRegeln.erinnerungFaellig(t.status, t.beginn, t.antwortfristStunden, jetzt)) {
                continue;
            }
            Map<UUID, AntwortService.Gespeichert> gespeichert = antworten.antworten(t.id);
            String text = BenachrichtigungsRegeln.textErinnerung(TerminBenachrichtigungen.bezeichnung(t),
                    TerminRegeln.antwortfrist(t.beginn, t.antwortfristStunden));
            for (UUID person : TerminBenachrichtigungen.mitglieder(t)) {
                AntwortService.Gespeichert a = gespeichert.get(person);
                if (a != null && a.status() != AntwortStatus.OFFEN) {
                    continue;
                }
                int neu = em.createNativeQuery("""
                                insert into erinnerung (termin_id, person_id, erinnert_am) values (?, ?, ?)
                                on conflict do nothing""")
                        .setParameter(1, t.id).setParameter(2, person).setParameter(3, jetzt).executeUpdate();
                if (neu == 1) {
                    nachrichten.add(new Nachricht(BenachrichtigungArt.ERINNERUNG, person, t.id, text));
                }
            }
        }
        benachrichtigungen.senden(nachrichten);
        return nachrichten.size();
    }
}
