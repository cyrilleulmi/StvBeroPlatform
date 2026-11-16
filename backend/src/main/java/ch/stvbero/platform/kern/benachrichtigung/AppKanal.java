package ch.stvbero.platform.kern.benachrichtigung;

import ch.stvbero.platform.kern.person.Person;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.UUID;

/** Die Liste in der App (`/benachrichtigungen`). Gibt es immer (E-56). */
@ApplicationScoped
public class AppKanal implements Kanal {

    @Override
    public void senden(Nachricht n, Instant zeitpunkt) {
        Benachrichtigung b = new Benachrichtigung();
        b.id = UUID.randomUUID();
        b.art = n.art();
        b.empfaenger = Person.getEntityManager().getReference(Person.class, n.empfaengerId());
        b.text = n.text();
        b.terminId = n.terminId();
        b.erstelltAm = zeitpunkt;
        b.persist();
    }
}
