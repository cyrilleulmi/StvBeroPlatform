package ch.stvbero.platform.kern.person;

import ch.stvbero.platform.kern.anmeldung.Angemeldet;
import ch.stvbero.platform.kern.fehler.FachFehler;
import ch.stvbero.platform.kern.team.Teamzugehoerigkeit;
import ch.stvbero.platform.kern.team.TeamzugehoerigkeitDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Personenverwaltung (PTF-01, PTF-11). Nur Admins (RR-04). */
@ApplicationScoped
public class PersonService {

    public List<PersonDto> liste(Angemeldet wer, String suche) {
        wer.pruefeAdmin();
        List<Person> personen;
        String s = PersonRegeln.bereinigen(suche);
        if (s == null) {
            personen = Person.list("order by lower(nachname), lower(vorname), id");
        } else {
            String muster = "%" + s.toLowerCase(Locale.ROOT) + "%";
            personen = Person.list("lower(vorname) like ?1 or lower(nachname) like ?1 "
                    + "or lower(concat(vorname, ' ', nachname)) like ?1 order by lower(nachname), lower(vorname), id", muster);
        }
        return personen.stream().map(PersonDto::von).toList();
    }

    public PersonDetailDto lesen(Angemeldet wer, UUID id) {
        wer.pruefeAdmin();
        Person p = finden(id);
        List<TeamzugehoerigkeitDto> zugehoerigkeiten = Teamzugehoerigkeit.<Teamzugehoerigkeit>list(
                        "person.id = ?1", id).stream()
                // Neueste Saison zuerst, darin nach dem Teamnamen dieser Saison (SAI-02).
                .sorted(Comparator.comparing((Teamzugehoerigkeit z) -> z.saison.von).reversed()
                        .thenComparing(z -> z.team.nameIn(z.saison), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(z -> z.team.id))
                .map(TeamzugehoerigkeitDto::von).toList();
        return new PersonDetailDto(p.id, p.vorname, p.nachname, p.email, p.admin, zugehoerigkeiten);
    }

    @Transactional
    public PersonDto anlegen(Angemeldet wer, PersonSchreiben eingabe) {
        wer.pruefeAdmin();
        Person p = new Person();
        p.id = UUID.randomUUID();
        uebernehmen(p, eingabe);
        p.persist();
        return PersonDto.von(p);
    }

    @Transactional
    public PersonDto aendern(Angemeldet wer, UUID id, PersonSchreiben eingabe) {
        wer.pruefeAdmin();
        Person p = finden(id);
        boolean kuenftigAdmin = Boolean.TRUE.equals(eingabe.admin());
        PersonRegeln.pruefeAdminEntzug(p.admin, kuenftigAdmin, Person.count("admin", true));
        uebernehmen(p, eingabe);
        return PersonDto.von(p);
    }

    private static void uebernehmen(Person p, PersonSchreiben eingabe) {
        p.vorname = PersonRegeln.bereinigen(eingabe.vorname());
        p.nachname = PersonRegeln.bereinigen(eingabe.nachname());
        p.email = PersonRegeln.bereinigen(eingabe.email());
        p.admin = Boolean.TRUE.equals(eingabe.admin());
    }

    public static Person finden(UUID id) {
        return Person.<Person>findByIdOptional(id)
                .orElseThrow(() -> FachFehler.nichtGefunden("Diese Person gibt es nicht."));
    }
}
