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

    /** Optional, for sorting and filtering people when they are put into a team (PTF-15). */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    public Gender gender;

    /** Year of birth («Jahrgang»), optional (PTF-15). */
    @Column(name = "birth_year")
    public Integer birthYear;

    /** Can drive a car to match days (DRV-18). */
    @Column(name = "can_drive")
    public boolean canDrive = true;

    /** Places of her car, herself included (DRV-18). */
    @Column(name = "car_seats")
    public int carSeats = 5;
}
