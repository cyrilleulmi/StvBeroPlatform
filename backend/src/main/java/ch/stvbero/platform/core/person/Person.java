package ch.stvbero.platform.core.person;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Ein Mensch im Verein. Es gibt jede Person genau einmal (siehe Fachmodell). */
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

    /** Admin ist eine Eigenschaft der Person, keine Funktion im Team (RR-04). */
    public boolean admin;
}
