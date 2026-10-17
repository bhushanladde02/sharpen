package io.sharpen.service;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends email, or does not — Sharpen ran for a month without sending any, and most deployments can stay that way.
 * Three modes, chosen by configuration: <b>off</b> (nothing is sent and every "send" button is hidden), <b>log</b>
 * (the message is written to the log and kept in memory — development and tests), and <b>Resend</b> (delivered
 * through Resend's HTTP API when {@code sharpen.mail.resend-api-key} is set). The API is one JSON POST, so no mail
 * library is needed; the From address must belong to a domain verified in Resend (two DNS records).
 */
@Service
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);
    private static final URI RESEND = URI.create("https://api.resend.com/emails");

    /** A message as handed to the provider; what the log mode keeps. */
    public record Sent(String to, String subject, String html, String text, String unsubscribeUrl) {}

    public enum Mode { OFF, LOG, RESEND }

    private final Mode mode;
    private final String from;
    private final String apiKey;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final List<Sent> outbox = new ArrayList<>();

    public Mailer(@Value("${sharpen.mail.resend-api-key:}") String apiKey,
                  @Value("${sharpen.mail.from:Sharpen <invites@sharpenscore.com>}") String from,
                  @Value("${sharpen.mail.log:false}") boolean logOnly) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.from = from;
        this.mode = !this.apiKey.isEmpty() ? Mode.RESEND : logOnly ? Mode.LOG : Mode.OFF;
        log.info("Mail: {}{}", mode, mode == Mode.RESEND ? " from " + from : "");
    }

    public boolean enabled() { return mode != Mode.OFF; }
    public Mode mode() { return mode; }

    /**
     * Delivers one message with both an HTML and a plain-text body. Throws {@link IOException} when the provider
     * refuses or cannot be reached, so the caller can tell the person rather than pretend.
     */
    public void send(String to, String subject, String html, String text, String unsubscribeUrl) throws IOException {
        switch (mode) {
            case OFF -> throw new IllegalStateException("mail is off");
            case LOG -> {
                synchronized (outbox) { outbox.add(new Sent(to, subject, html, text, unsubscribeUrl)); if (outbox.size() > 50) outbox.remove(0); }
                log.info("Mail (log mode) to {}: {}\n{}", to, subject, text);
            }
            case RESEND -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("from", from);
                body.put("to", List.of(to));
                body.put("subject", subject);
                body.put("html", html);
                body.put("text", text);
                if (unsubscribeUrl != null) body.put("headers", Map.of("List-Unsubscribe", "<" + unsubscribeUrl + ">"));
                HttpRequest req = HttpRequest.newBuilder(RESEND)
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build();
                HttpResponse<String> res;
                try {
                    res = http.send(req, HttpResponse.BodyHandlers.ofString());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while sending", e);
                }
                if (res.statusCode() / 100 != 2) {
                    log.warn("Mail to {} refused by provider: HTTP {}", to, res.statusCode());   // the body may echo the address; not logged
                    throw new IOException("mail provider answered HTTP " + res.statusCode());
                }
                log.info("Mail sent: {}", subject);
            }
        }
    }

    /** Log mode only: what has been "sent" since start, oldest first. */
    public List<Sent> outbox() { synchronized (outbox) { return List.copyOf(outbox); } }
}
