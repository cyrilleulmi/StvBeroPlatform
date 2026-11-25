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

/** Managing persons (PTF-01, PTF-11). Admins and coaches (RR-03, E-69); only admins change the admin flag (RR-04). */
@ApplicationScoped
public class PersonService {

    public List<PersonDto> list(CurrentUser user, String search) {
        user.checkAllTeams();
        List<Person> persons;
        String term = PersonRules.clean(search);
        if (term == null) {
            persons = Person.list("order by lower(lastName), lower(firstName), id");
        } else {
            String pattern = "%" + term.toLowerCase(Locale.ROOT) + "%";
            persons = Person.list("lower(firstName) like ?1 or lower(lastName) like ?1 "
                    + "or lower(concat(firstName, ' ', lastName)) like ?1 order by lower(lastName), lower(firstName), id", pattern);
        }
        return persons.stream().map(PersonDto::of).toList();
    }

    public PersonDetailDto get(CurrentUser user, UUID id) {
        user.checkAllTeams();
        Person p = find(id);
        List<TeamMembershipDto> memberships = TeamMembership.<TeamMembership>list(
                        "person.id = ?1", id).stream()
                // Newest season first, within it by the team name of that season (SAI-02).
                .sorted(Comparator.comparing((TeamMembership m) -> m.season.startDate).reversed()
                        .thenComparing(m -> m.team.nameIn(m.season), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(m -> m.team.id))
                .map(TeamMembershipDto::of).toList();
        return new PersonDetailDto(p.id, p.firstName, p.lastName, p.email, p.admin, p.gender, p.birthYear, memberships);
    }

    @Transactional
    public PersonDto create(CurrentUser user, PersonInput input) {
        user.checkAllTeams();
        Person p = new Person();
        p.id = UUID.randomUUID();
        apply(p, input, PersonRules.adminFlag(user.admin(), false, input.admin()));
        p.persist();
        return PersonDto.of(p);
    }

    @Transactional
    public PersonDto update(CurrentUser user, UUID id, PersonInput input) {
        user.checkAllTeams();
        Person p = find(id);
        boolean staysAdmin = PersonRules.adminFlag(user.admin(), p.admin, input.admin());
        PersonRules.checkAdminRevocation(p.admin, staysAdmin, Person.count("admin", true));
        apply(p, input, staysAdmin);
        return PersonDto.of(p);
    }

    private static void apply(Person p, PersonInput input, boolean admin) {
        p.firstName = PersonRules.clean(input.firstName());
        p.lastName = PersonRules.clean(input.lastName());
        p.email = PersonRules.clean(input.email());
        p.admin = admin;
        p.gender = input.gender();
        p.birthYear = input.birthYear();
    }

    public static Person find(UUID id) {
        return Person.<Person>findByIdOptional(id)
                .orElseThrow(() -> DomainError.notFound("Diese Person gibt es nicht."));
    }
}
