package ch.stvbero.platform.core.person;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Optional;
import java.util.UUID;

/** One login. Separate from the person (PTF-09); today every account belongs to exactly one person. */
@Entity
@Table(name = "account")
public class Account extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "person_id")
    public Person person;

    /** Key for signing in in test mode, e.g. {@code petra}. */
    @Column(name = "login_key")
    public String loginKey;

    public static Optional<Account> withLoginKey(String loginKey) {
        return find("loginKey", loginKey).firstResultOptional();
    }
}
