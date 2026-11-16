package ch.stvbero.platform.kern.benachrichtigung;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.uhr.Uhr;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Versand und Liste der Benachrichtigungen (BEN). Die Auslöser bestimmen Empfänger und Text und geben die
 * Nachrichten hierher; diese Klasse verteilt sie an alle {@link Kanal}e (BEN-11). Die eigene Liste sieht nur
 * die Person selbst (RR-07).
 */
@ApplicationScoped
public class Benachrichtigungen {

    @Inject
    @Any
    Instance<Kanal> kanaele;

    @Inject
    Uhr uhr;

    /** Nachrichten über alle Kanäle versenden, mit dem Zeitpunkt der Backend-Uhr. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void senden(Collection<Nachricht> nachrichten) {
        if (nachrichten.isEmpty()) {
            return;
        }
        Instant jetzt = uhr.jetzt();
        for (Nachricht n : nachrichten) {
            for (Kanal k : kanaele) {
                k.senden(n, jetzt);
            }
        }
    }

    /** Eigene Benachrichtigungen, neueste zuerst; mit {@code nurUngelesene} nur die ungelesenen. */
    public List<BenachrichtigungDto> eigene(Angemeldet wer, boolean nurUngelesene) {
        String filter = nurUngelesene ? " and gelesenAm is null" : "";
        return Benachrichtigung.<Benachrichtigung>find("empfaenger.id = ?1" + filter
                        + " order by erstelltAm desc, laufnummer desc", wer.personId())
                .list().stream().map(BenachrichtigungDto::von).toList();
    }

    /**
     * Als gelesen markieren (zustaende.md, Benachrichtigung). Mehrfach markieren ändert nichts. Die
     * Benachrichtigung einer anderen Person: 403 (RR-07).
     */
    @Transactional
    public void gelesen(Angemeldet wer, UUID id) {
        Benachrichtigung b = Benachrichtigung.<Benachrichtigung>findByIdOptional(id)
                .orElseThrow(() -> FachFehler.nichtGefunden("Diese Benachrichtigung gibt es nicht."));
        if (!b.empfaenger.id.equals(wer.personId())) {
            throw FachFehler.keineBerechtigung();
        }
        if (b.gelesenAm == null) {
            b.gelesenAm = uhr.jetzt();
        }
    }

    /** Alle Benachrichtigungen aller Personen in der Reihenfolge ihrer Entstehung (nur Test-Hilfe). */
    public List<BenachrichtigungDto> alle() {
        return Benachrichtigung.<Benachrichtigung>find("order by erstelltAm, laufnummer")
                .list().stream().map(BenachrichtigungDto::von).toList();
    }
}
