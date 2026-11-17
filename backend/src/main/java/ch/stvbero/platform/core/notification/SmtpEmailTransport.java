package ch.stvbero.platform.core.notification;

import io.quarkus.arc.properties.UnlessBuildProperty;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * E-mails over SMTP in profile {@code prod} (F-19). On only if {@code STV_SMTP_HOST} and {@code STV_MAIL_FROM}
 * are set (docs/operations.md); otherwise the e-mail channel is off and the app runs normally.
 */
@ApplicationScoped
@UnlessBuildProperty(name = "stv.test-support", stringValue = "true")
public class SmtpEmailTransport implements EmailTransport {

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "stv.email.from")
    Optional<String> from;

    @ConfigProperty(name = "stv.email.smtp-host")
    Optional<String> host;

    @Override
    public boolean available() {
        return from.filter(f -> !f.isBlank()).isPresent() && host.filter(h -> !h.isBlank()).isPresent();
    }

    @Override
    public void send(DeliveryRules.Email email) {
        if (!available()) {
            throw new IllegalStateException("E-mail is not configured");
        }
        mailer.send(Mail.withHtml(email.to(), email.subject(), email.html()).setText(email.text()).setFrom(from.get()));
    }
}
