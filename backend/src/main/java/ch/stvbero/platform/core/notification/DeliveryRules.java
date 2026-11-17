package ch.stvbero.platform.core.notification;

import ch.stvbero.platform.core.event.NotificationRules;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rules for e-mail and push (F-19, BEN-10): who gets e-mails, what an e-mail and a push say, and when a failed
 * delivery is tried again. Pure logic without a database.
 */
public final class DeliveryRules {

    /** Waiting time after the 1st, 2nd, … failed attempt. After the last one the delivery counts as failed. */
    static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));

    /** Attempts in total: the first one and one per waiting time. */
    public static final int MAX_ATTEMPTS = RETRY_DELAYS.size() + 1;

    /** Name of the app in e-mails and pushes. */
    public static final String APP_NAME = "STV Bero";

    static final String PROFILE_PATH = "/profil";

    private DeliveryRules() {
    }

    // ------------------------------------------------------------------ Who

    /**
     * E-mails are on by default if the person has an e-mail address (F-19, criterion 2); a person can switch them
     * off. Without an address there are no e-mails, whatever was chosen.
     *
     * @param preference the person's choice, {@code null} if she never chose
     */
    public static boolean emailOn(String address, Boolean preference) {
        return hasAddress(address) && (preference == null || preference);
    }

    public static boolean hasAddress(String address) {
        return address != null && !address.isBlank();
    }

    // ------------------------------------------------------------------ When

    /**
     * When the next attempt is due after the given number of failed attempts; empty if there is none left (F-19,
     * criterion 7).
     */
    public static Optional<Instant> nextAttempt(int failedAttempts, Instant now) {
        if (failedAttempts < 1 || failedAttempts >= MAX_ATTEMPTS) {
            return Optional.empty();
        }
        return Optional.of(now.plus(RETRY_DELAYS.get(failedAttempts - 1)));
    }

    /** HTTP status codes with which a push service reports a subscription as gone (RFC 8030). */
    public static boolean pushExpired(int status) {
        return status == 404 || status == 410;
    }

    // ------------------------------------------------------------------ What

    /** Where the e-mail button and the tap on the push lead: the event (BEN-12), otherwise the list. */
    public static String path(UUID eventId) {
        return eventId == null ? "/benachrichtigungen" : "/termine/" + eventId;
    }

    /**
     * Subject of the e-mail and title of the push: the essence in a few words (F-19, criterion 5), e.g.
     * «Abgesagt: Training Di 24.11.».
     *
     * @param eventTitle title of the event, {@code null} without event
     * @param eventStart start of the event, {@code null} without event
     */
    public static String subject(NotificationType type, String eventTitle, Instant eventStart) {
        String essence = switch (type) {
            case REMINDER -> "Bitte gib Bescheid";
            case LATE_CANCELLATION -> "Kurzfristige Abmeldung";
            case CALL_UP_PUBLISHED -> "Aufgebot ist da";
            case CALL_UP_CHANGED -> "Aufgebot geändert";
            case CALLED_UP_PLAYER_DECLINED -> "Abmeldung im Aufgebot";
            case MATCH_CHANGED -> "Spiel geändert";
            case EVENT_CANCELLED -> "Abgesagt";
            case EVENT_RESCHEDULED -> "Verschoben";
            case DRIVER_LIST_CHANGED -> "Fahrerliste geändert";
        };
        if (eventTitle == null) {
            return essence;
        }
        // The text of a rescheduling names the old and the new date; the subject only the event.
        String event = type == NotificationType.EVENT_RESCHEDULED || eventStart == null
                ? eventTitle : NotificationRules.label(eventTitle, eventStart);
        return truncate(essence + ": " + event, 200);
    }

    /** Label of the button in the e-mail. */
    static String buttonText(String path) {
        return path.startsWith("/termine/") ? "Termin ansehen" : "In der App öffnen";
    }

    /** The parts of an e-mail; the same content as plain text and as HTML. */
    public record Email(String to, String subject, String text, String html) {
    }

    /**
     * E-mail in German, plain and readable on a phone (F-19, criterion 5): greeting, the sentence of the
     * notification, a button to the event and a line on how to switch e-mails off.
     */
    public static Email email(String to, String firstName, String subject, String sentence, String appUrl,
                              String path) {
        String base = stripSlash(appUrl);
        String link = base + path;
        String profile = base + PROFILE_PATH;
        String greeting = firstName == null || firstName.isBlank() ? "Hallo" : "Hallo " + firstName;
        String button = buttonText(path);
        String footer = "Du bekommst diese E-Mail, weil E-Mails in deinem Profil eingeschaltet sind.";
        String off = "Ausschalten kannst du sie im Profil unter «Benachrichtigungen»";
        String text = greeting + "\n\n"
                + sentence + "\n\n"
                + button + ": " + link + "\n\n"
                + "-- \n" + APP_NAME + "\n"
                + footer + " " + off + ": " + profile + "\n";
        String html = """
                <!DOCTYPE html>
                <html lang="de-CH">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title></head>
                <body style="margin:0;padding:0;background:#f0f5f2;color:#1f2a26;font-family:-apple-system,'Segoe UI',Roboto,Arial,sans-serif;">
                <div style="max-width:480px;margin:0 auto;padding:24px 16px;">
                <p style="margin:0 0 16px;font-size:17px;line-height:1.5;">%s</p>
                <p style="margin:0 0 24px;font-size:17px;line-height:1.5;">%s</p>
                <p style="margin:0 0 32px;"><a href="%s" style="display:inline-block;padding:14px 22px;border-radius:12px;background:#2c7f69;color:#ffffff;font-size:17px;font-weight:700;text-decoration:none;">%s</a></p>
                <p style="margin:0;font-size:14px;line-height:1.5;color:#4a5a54;">%s<br>%s<br>%s <a href="%s" style="color:#2c7f69;">%s</a>.</p>
                </div>
                </body>
                </html>
                """.formatted(escape(subject), escape(greeting), escape(sentence), escape(link), escape(button),
                escape(APP_NAME), escape(footer), escape(off) + ":", escape(profile), escape(profile));
        return new Email(to, subject, text, html);
    }

    /** Content of a push as JSON for the service worker: title, text, path to open, tag against duplicates. */
    public static String pushPayload(UUID notificationId, String subject, String sentence, String path) {
        return "{\"title\":" + json(subject) + ",\"body\":" + json(sentence) + ",\"url\":" + json(path)
                + ",\"tag\":" + json(notificationId.toString()) + "}";
    }

    // ------------------------------------------------------------------ Device

    /** Readable name of a device from its user agent, e.g. «Chrome auf Android»; at most 100 characters. */
    public static String deviceLabel(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unbekanntes Gerät";
        }
        String ua = userAgent;
        String system;
        if (ua.contains("iPhone")) {
            system = "iPhone";
        } else if (ua.contains("iPad")) {
            system = "iPad";
        } else if (ua.contains("Android")) {
            system = "Android";
        } else if (ua.contains("Windows")) {
            system = "Windows";
        } else if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            system = "Mac";
        } else if (ua.contains("Linux")) {
            system = "Linux";
        } else {
            system = null;
        }
        String browser;
        if (ua.contains("Edg/")) {
            browser = "Edge";
        } else if (ua.contains("Firefox/") || ua.contains("FxiOS/")) {
            browser = "Firefox";
        } else if (ua.contains("Chrome/") || ua.contains("CriOS/")) {
            browser = "Chrome";
        } else if (ua.contains("Safari/")) {
            browser = "Safari";
        } else {
            browser = null;
        }
        if (browser == null && system == null) {
            return "Unbekanntes Gerät";
        }
        if (browser == null) {
            return system;
        }
        return system == null ? browser : browser + " auf " + system;
    }

    // ------------------------------------------------------------------ Helpers

    static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
