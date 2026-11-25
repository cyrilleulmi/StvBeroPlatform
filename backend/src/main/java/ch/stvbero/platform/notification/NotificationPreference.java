package ch.stvbero.platform.notification;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** The person's choice for e-mails (F-19). Without a row the default applies (DeliveryRules#emailOn). */
@Entity
@Table(name = "notification_preference")
public class NotificationPreference extends PanacheEntityBase {

    @Id
    @Column(name = "person_id")
    public UUID personId;

    public boolean email;
}
