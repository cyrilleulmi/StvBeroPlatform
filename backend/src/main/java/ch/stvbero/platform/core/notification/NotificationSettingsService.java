package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.person.Person;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * The channels of a person (F-19): e-mail on or off and the devices with push. Everyone sees and changes only
 * their own (RR-07).
 */
@ApplicationScoped
public class NotificationSettingsService {

    @Inject
    EmailTransport email;

    @Inject
    PushTransport push;

    @Inject
    BackendClock clock;

    public NotificationSettingsDto mine(UUID personId) {
        Person person = Person.findById(personId);
        List<PushDeviceDto> devices = PushSubscription.<PushSubscription>list("person.id = ?1 order by createdAt, sequenceNumber",
                personId).stream().map(PushDeviceDto::of).toList();
        String address = DeliveryRules.hasAddress(person.email) ? person.email.trim() : null;
        return new NotificationSettingsDto(emailOn(person), address, email.available(), push.available(),
                push.publicKey(), devices);
    }

    /** E-mails on or off. Switching on without an e-mail address: 422. */
    @Transactional
    public NotificationSettingsDto change(UUID personId, NotificationSettingsInput input) {
        Person person = Person.findById(personId);
        if (input.email() && !DeliveryRules.hasAddress(person.email)) {
            throw DomainError.ruleViolation("email", "Keine E-Mail-Adresse",
                    "Für E-Mails braucht es eine E-Mail-Adresse. Die Verwaltung kann sie bei deiner Person eintragen.",
                    "BEN-10");
        }
        // Upsert: two concurrent changes never result in a 500 (BEN-10, states.md)
        Person.getEntityManager().createNativeQuery("""
                        insert into notification_preference (person_id, email) values (?1, ?2)
                        on conflict (person_id) do update set email = excluded.email""")
                .setParameter(1, personId).setParameter(2, input.email()).executeUpdate();
        return mine(personId);
    }

    /**
     * Registers this device for push (F-19, criterion 3). The same endpoint again updates the keys; if another
     * person used the device before, it now belongs to the person signed in on it.
     */
    @Transactional
    public PushDeviceDto register(UUID personId, PushSubscriptionInput input, String userAgent) {
        if (!push.available()) {
            throw DomainError.conflict("Push nicht verfügbar", "Push ist zurzeit nicht verfügbar.", "BEN-10");
        }
        String endpoint = input.endpoint().trim();
        checkEndpoint(endpoint);
        checkKeys(input.keys());
        // Insert if new, then lock the row: two concurrent registrations of the same device never result in a 500
        Person.getEntityManager().createNativeQuery("""
                        insert into push_subscription (id, person_id, endpoint, p256dh, auth, label, created_at)
                        values (?1, ?2, ?3, ?4, ?5, ?6, ?7) on conflict (endpoint) do nothing""")
                .setParameter(1, UUID.randomUUID()).setParameter(2, personId).setParameter(3, endpoint)
                .setParameter(4, input.keys().p256dh().trim()).setParameter(5, input.keys().auth().trim())
                .setParameter(6, DeliveryRules.deviceLabel(userAgent)).setParameter(7, clock.now())
                .executeUpdate();
        PushSubscription subscription = PushSubscription.<PushSubscription>find("endpoint", endpoint)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (!subscription.person.id.equals(personId)) {
            subscription.person = Person.getEntityManager().getReference(Person.class, personId);
            subscription.createdAt = clock.now();
        }
        fill(subscription, input, userAgent);
        return PushDeviceDto.of(subscription);
    }

    /**
     * Push off for a device. Only the own devices (RR-07). A bulk delete instead of deleting the loaded entity: two
     * concurrent removals of the same device both succeed and never give a 500 (BEN-16, states.md).
     */
    @Transactional
    public void remove(UUID personId, UUID subscriptionId) {
        PushSubscription subscription = PushSubscription.<PushSubscription>findByIdOptional(subscriptionId)
                .orElseThrow(() -> DomainError.notFound("Dieses Gerät gibt es nicht."));
        if (!subscription.person.id.equals(personId)) {
            throw DomainError.forbidden();
        }
        PushSubscription.delete("id = ?1 and person.id = ?2", subscriptionId, personId);
    }

    // ------------------------------------------------------------------ Used by the channels

    boolean emailOn(Person person) {
        NotificationPreference preference = NotificationPreference.findById(person.id);
        return DeliveryRules.emailOn(person.email, preference == null ? null : preference.email);
    }

    String subject(Message message) {
        Event event = message.eventId() == null ? null : Event.findById(message.eventId());
        return event == null
                ? DeliveryRules.subject(message.type(), null, null)
                : DeliveryRules.subject(message.type(), event.title, event.start);
    }

    // ------------------------------------------------------------------ Helpers

    private static void fill(PushSubscription subscription, PushSubscriptionInput input, String userAgent) {
        subscription.p256dh = input.keys().p256dh().trim();
        subscription.auth = input.keys().auth().trim();
        subscription.label = DeliveryRules.deviceLabel(userAgent);
    }

    private static void checkEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            if ("https".equals(uri.getScheme()) && uri.getHost() != null) {
                return;
            }
        } catch (IllegalArgumentException e) {
            // handled below
        }
        throw DomainError.invalidField("endpoint", "Die Adresse des Push-Dienstes ist ungültig.");
    }

    private static void checkKeys(PushSubscriptionInput.PushSubscriptionKeys keys) {
        try {
            WebPush.publicKey(WebPush.decode(keys.p256dh()));
        } catch (IllegalArgumentException e) {
            throw DomainError.invalidField("keys.p256dh", "Der Schlüssel des Geräts ist ungültig.");
        }
        try {
            if (WebPush.decode(keys.auth()).length != 16) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw DomainError.invalidField("keys.auth", "Der Schlüssel des Geräts ist ungültig.");
        }
    }
}
