package ch.stvbero.platform.core.person;

import ch.stvbero.platform.core.auth.CurrentUser;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.team.TeamMembership;
import ch.stvbero.platform.core.team.TeamMembershipDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Personenverwaltung (PTF-01, PTF-11). Nur Admins (RR-04). */
@ApplicationScoped
public class PersonService {

    public List<PersonDto> list(CurrentUser user, String search) {
        user.checkAdmin();
        List<Person> persons;
        String s = PersonRules.clean(search);
        if (s == null) {
            persons = Person.list("order by lower(lastName), lower(firstName), id");
        } else {
            String pattern = "%" + s.toLowerCase(Locale.ROOT) + "%";
            persons = Person.list("lower(firstName) like ?1 or lower(lastName) like ?1 "
                    + "or lower(concat(firstName, ' ', lastName)) like ?1 order by lower(lastName), lower(firstName), id", pattern);
        }
        return persons.stream().map(PersonDto::of).toList();
    }

    public PersonDetailDto get(CurrentUser user, UUID id) {
        user.checkAdmin();
        Person p = find(id);
        List<TeamMembershipDto> memberships = TeamMembership.<TeamMembership>list(
                        "person.id = ?1", id).stream()
                // Neueste Saison zuerst, darin nach dem Teamnamen dieser Saison (SAI-02).
                .sorted(Comparator.comparing((TeamMembership z) -> z.season.startDate).reversed()
                        .thenComparing(z -> z.team.nameIn(z.season), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(z -> z.team.id))
                .map(TeamMembershipDto::of).toList();
        return new PersonDetailDto(p.id, p.firstName, p.lastName, p.email, p.admin, memberships);
    }

    @Transactional
    public PersonDto create(CurrentUser user, PersonInput input) {
        user.checkAdmin();
        Person p = new Person();
        p.id = UUID.randomUUID();
        apply(p, input);
        p.persist();
        return PersonDto.of(p);
    }

    @Transactional
    public PersonDto update(CurrentUser user, UUID id, PersonInput input) {
        user.checkAdmin();
        Person p = find(id);
        boolean futureAdmin = Boolean.TRUE.equals(input.admin());
        PersonRules.checkAdminRevocation(p.admin, futureAdmin, Person.count("admin", true));
        apply(p, input);
        return PersonDto.of(p);
    }

    private static void apply(Person p, PersonInput input) {
        p.firstName = PersonRules.clean(input.firstName());
        p.lastName = PersonRules.clean(input.lastName());
        p.email = PersonRules.clean(input.email());
        p.admin = Boolean.TRUE.equals(input.admin());
    }

    public static Person find(UUID id) {
        return Person.<Person>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diese Person gibt es nicht."));
    }
}
