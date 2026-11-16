package ch.stvbero.platform.kern.termin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static ch.stvbero.platform.kern.termin.AntwortStatus.DABEI;
import static ch.stvbero.platform.kern.termin.AntwortStatus.NICHT_DABEI;
import static ch.stvbero.platform.kern.termin.AntwortStatus.OFFEN;
import static ch.stvbero.platform.kern.termin.AntwortStatus.VIELLEICHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F-06: Regeln, wann wer benachrichtigt wird (BEN-01 bis BEN-07, BEN-13). */
class BenachrichtigungsRegelnTest {

    /** Termin 01: Di 17.11.2026 18:30, Antwortfrist 24 Stunden, also Mo 16.11. 18:30. */
    private static final Instant BEGINN = zeit("2026-11-17T18:30:00+01:00");

    private static Instant zeit(String iso) {
        return OffsetDateTime.parse(iso).toInstant();
    }

    @Test
    void ben01ErinnerungZwoelfStundenVorDerFristBisZurFrist() {
        assertFalse(BenachrichtigungsRegeln.erinnerungFaellig(TerminStatus.GEPLANT, BEGINN, 24, zeit("2026-11-16T06:29:59+01:00")));
        assertTrue(BenachrichtigungsRegeln.erinnerungFaellig(TerminStatus.GEPLANT, BEGINN, 24, zeit("2026-11-16T06:30:00+01:00")));
        assertTrue(BenachrichtigungsRegeln.erinnerungFaellig(TerminStatus.GEPLANT, BEGINN, 24, zeit("2026-11-16T18:29:59+01:00")));
        assertFalse(BenachrichtigungsRegeln.erinnerungFaellig(TerminStatus.GEPLANT, BEGINN, 24, zeit("2026-11-16T18:30:00+01:00")));
        // Andere Frist verschiebt das Fenster
        assertTrue(BenachrichtigungsRegeln.erinnerungFaellig(TerminStatus.GEPLANT, BEGINN, 0, zeit("2026-11-17T07:00:00+01:00")));
        // Abgesagte Termine: keine Erinnerung
        assertFalse(BenachrichtigungsRegeln.erinnerungFaellig(TerminStatus.ABGESAGT, BEGINN, 24, zeit("2026-11-16T07:00:00+01:00")));
    }

    @Test
    void ben02KurzfristigeAbsageNurBeimWechselWegVonDabei() {
        assertTrue(BenachrichtigungsRegeln.kurzfristigeAbsage(DABEI, NICHT_DABEI, true));
        assertTrue(BenachrichtigungsRegeln.kurzfristigeAbsage(DABEI, VIELLEICHT, true));
        // Vor der Frist ist es nicht kurzfristig
        assertFalse(BenachrichtigungsRegeln.kurzfristigeAbsage(DABEI, NICHT_DABEI, false));
        // Bleibt kurzfristig (vielleicht → nicht dabei): nicht nochmals
        assertFalse(BenachrichtigungsRegeln.kurzfristigeAbsage(VIELLEICHT, NICHT_DABEI, true));
        assertFalse(BenachrichtigungsRegeln.kurzfristigeAbsage(OFFEN, NICHT_DABEI, false));
    }

    @Test
    void ben04AbmeldungEinerAufgebotenen() {
        assertTrue(BenachrichtigungsRegeln.abmeldungAufgebotene(true, true, DABEI, NICHT_DABEI));
        assertTrue(BenachrichtigungsRegeln.abmeldungAufgebotene(true, true, DABEI, VIELLEICHT));
        assertTrue(BenachrichtigungsRegeln.abmeldungAufgebotene(true, true, OFFEN, NICHT_DABEI));
        assertTrue(BenachrichtigungsRegeln.abmeldungAufgebotene(true, true, VIELLEICHT, NICHT_DABEI));
        assertFalse(BenachrichtigungsRegeln.abmeldungAufgebotene(true, true, NICHT_DABEI, VIELLEICHT));
        assertFalse(BenachrichtigungsRegeln.abmeldungAufgebotene(true, true, NICHT_DABEI, NICHT_DABEI));
        assertFalse(BenachrichtigungsRegeln.abmeldungAufgebotene(true, true, NICHT_DABEI, DABEI));
        // Nicht aufgeboten oder nur Entwurf (AUF-23)
        assertFalse(BenachrichtigungsRegeln.abmeldungAufgebotene(false, true, DABEI, NICHT_DABEI));
        assertFalse(BenachrichtigungsRegeln.abmeldungAufgebotene(true, false, DABEI, NICHT_DABEI));
    }

    @Test
    void ben05NurBetroffene() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        BenachrichtigungsRegeln.Aenderung aenderung = BenachrichtigungsRegeln.aufgebotGeaendert(Set.of(a, b), Set.of(b, c));
        assertEquals(Set.of(c), aenderung.neu());
        assertEquals(Set.of(a), aenderung.entfernt());
    }

    @Test
    void ben07VerschiebungIstEineAndereBeginnzeit() {
        assertTrue(BenachrichtigungsRegeln.verschoben(BEGINN, zeit("2026-11-17T19:00:00+01:00")));
        assertFalse(BenachrichtigungsRegeln.verschoben(BEGINN, zeit("2026-11-17T18:30:00+01:00")));
    }

    @Test
    void ausloeserWirdNichtBenachrichtigt() {
        UUID petra = UUID.randomUUID();
        UUID anna = UUID.randomUUID();
        assertEquals(List.of(anna), List.copyOf(BenachrichtigungsRegeln.ohneAusloeser(List.of(petra, anna, anna), petra)));
    }

    @Test
    void texte() {
        assertEquals("Training Di 17.11.", BenachrichtigungsRegeln.bezeichnung("Training", BEGINN));
        assertEquals("Anna Muster hat sich kurzfristig für Training Di 17.11. abgemeldet.",
                BenachrichtigungsRegeln.textKurzfristig("Anna Muster", "Training Di 17.11.", NICHT_DABEI));
        assertEquals("Erinnerung: Bitte gib bis Mo 16.11. 18:30 Bescheid, ob du bei Training Di 17.11. dabei bist.",
                BenachrichtigungsRegeln.textErinnerung("Training Di 17.11.", zeit("2026-11-16T18:30:00+01:00")));
        assertEquals("Training Di 17.11. ist verschoben auf Mi 18.11. 19:00.",
                BenachrichtigungsRegeln.textVerschoben("Training Di 17.11.", zeit("2026-11-18T19:00:00+01:00")));
    }
}
