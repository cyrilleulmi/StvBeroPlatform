package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.clock.BackendClock;
import ch.stvbero.platform.core.error.DomainError;
import ch.stvbero.platform.core.event.Event;
import ch.stvbero.platform.core.person.Person;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
        NotificationPreference preference = NotificationPreference.findById(personId);
        if (preference == null) {
            preference = new NotificationPreference();
            preference.personId = personId;
            preference.email = input.email();
            preference.persist();
        } else {
            preference.email = input.email();
        }
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
        PushSubscription subscription = PushSubscription.<PushSubscription>find("endpoint", endpoint).firstResult();
        if (subscription == null) {
            subscription = new PushSubscription();
            subscription.id = UUID.randomUUID();
            subscription.endpoint = endpoint;
            subscription.createdAt = clock.now();
            subscription.person = Person.getEntityManager().getReference(Person.class, personId);
            fill(subscription, input, userAgent);
            subscription.persist();
        } else {
            if (!subscription.person.id.equals(personId)) {
                subscription.person = Person.getEntityManager().getReference(Person.class, personId);
                subscription.createdAt = clock.now();
            }
            fill(subscription, input, userAgent);
        }
        return PushDeviceDto.of(subscription);
    }

    /** Push off for a device. Only the own devices (RR-07). */
    @Transactional
    public void remove(UUID personId, UUID subscriptionId) {
        PushSubscription subscription = PushSubscription.<PushSubscription>findByIdOptional(subscriptionId)
                .orElseThrow(() -> DomainError.notFound("Dieses Gerät gibt es nicht."));
        if (!subscription.person.id.equals(personId)) {
            throw DomainError.forbidden();
        }
        subscription.delete();
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
