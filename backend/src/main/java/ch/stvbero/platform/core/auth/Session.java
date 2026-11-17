package ch.stvbero.platform.core.auth;

import ch.stvbero.platform.core.person.Account;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "session")
public class Session extends PanacheEntityBase {

    @Id
    public String token;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id")
    public Account account;

    @Column(name = "created_at")
    public Instant createdAt;
}
