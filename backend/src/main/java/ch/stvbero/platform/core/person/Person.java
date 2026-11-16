package ch.stvbero.platform.core.person;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** A human being in the club. Every person exists exactly once (see docs/domain-model.md). */
@Entity
@Table(name = "person")
public class Person extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "first_name")
    public String firstName;

    @Column(name = "last_name")
    public String lastName;

    public String email;

    /** Admin is a property of the person, not a team role (RR-04). */
    public boolean admin;
}
