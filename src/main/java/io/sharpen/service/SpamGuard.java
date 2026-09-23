package io.sharpen.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Keeps the contact form usable by people and useless to bots, without a captcha or a third-party service.
 * Three checks on the way in — a honeypot field that only bots fill, a signed timestamp that proves the form
 * was open for at least a few seconds, and a per-address rate limit — and one classifier on the way out that
 * folds obvious vendor spam ("we can place your website on Google's first page") away from real messages.
 * Nothing that passes the first three checks is ever dropped: the classifier only labels.
 */
@Service
public class SpamGuard {

    /** A form has to have been open at least this long — bots post within milliseconds. */
    static final long MIN_SECONDS = 4;
    /** …and no longer than this, so a token cannot be harvested and replayed for weeks. */
    static final long MAX_SECONDS = 24 * 3600;
    /** Messages one address may send per hour; the sixth is refused with a polite error, not stored. */
    static final int PER_HOUR = 5;

    private static final Pattern SPAMMY = Pattern.compile(
            "google'?s? (search )?index|first page of google|google 1st page|rank(ing)? (higher|on google)|" +
            "seo (package|action plan|services|audit|expert|consultant)|price ?list|pricing options|quote & price|" +
            "web(site)? (design|redesign|development) ?(&|and)|mobile app development|digital marketing|" +
            "engaging video|explainer video|link building|backlinks|guest post|" +
            "prices start|our prices|samples of our|seeing samples|i just visited|going through your website|" +
            "lot of potential|may i send|brief proposal|send you a quote|place your website|" +
            "\\.pro\\b|\\.top\\b|\\.xyz\\b|" +
            "dear sir/madam|hi sharpenscore|hey team sharpenscore|hello http",
            Pattern.CASE_INSENSITIVE);

    private final byte[] secret = new byte[32];
    private final Map<String, long[]> perAddress = new ConcurrentHashMap<>();   // address → [windowStartEpochSec, count]

    public SpamGuard() {
        new SecureRandom().nextBytes(secret);
    }

    /** Value for the hidden "t" field when the form is rendered: seconds + signature. */
    public String token() {
        long now = System.currentTimeMillis() / 1000;
        return now + "." + sign(Long.toString(now));
    }

    /** Why a submission is refused, or null when it may be stored. */
    public enum Refusal { HONEYPOT, TOO_FAST, EXPIRED, RATE_LIMITED }

    public Refusal check(String honeypot, String token, String address) {
        if (honeypot != null && !honeypot.isBlank()) return Refusal.HONEYPOT;
        long issued = issuedAt(token);
        if (issued < 0) return Refusal.EXPIRED;
        long age = System.currentTimeMillis() / 1000 - issued;
        if (age < MIN_SECONDS) return Refusal.TOO_FAST;
        if (age > MAX_SECONDS) return Refusal.EXPIRED;
        if (address != null && !allow(address)) return Refusal.RATE_LIMITED;
        return null;
    }

    /** True for the pattern of unsolicited vendor mail; kept deliberately narrow so a real user is never hidden. */
    public boolean looksLikeSpam(String message, String email) {
        if (message == null) return false;
        String m = message.toLowerCase(Locale.ROOT);
        int hits = 0;
        var matcher = SPAMMY.matcher(m);
        while (matcher.find()) hits++;
        if (email != null && email.toLowerCase(Locale.ROOT).matches(".*@(search|index|seo|domains?)[-.].*")) hits += 2;
        return hits >= 2;   // one marketing phrase can be innocent; two in one message is a pitch
    }

    private boolean allow(String address) {
        long now = System.currentTimeMillis() / 1000;
        long[] w = perAddress.compute(address, (k, v) -> {
            if (v == null || now - v[0] >= 3600) return new long[] {now, 1};
            v[1]++;
            return v;
        });
        if (perAddress.size() > 10_000) perAddress.clear();   // never grows without bound
        return w[1] <= PER_HOUR;
    }

    /** Seconds since the epoch when the token was issued, or -1 if it is missing or the signature is wrong. */
    long issuedAt(String token) {
        if (token == null) return -1;
        int dot = token.indexOf('.');
        if (dot <= 0) return -1;
        String ts = token.substring(0, dot), sig = token.substring(dot + 1);
        if (!sign(ts).equals(sig)) return -1;
        try { return Long.parseLong(ts); } catch (NumberFormatException e) { return -1; }
    }

    private String sign(String s) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(s.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** For tests: a valid token that claims to have been issued {@code seconds} ago. */
    public String tokenIssuedAgo(long seconds) {
        long then = System.currentTimeMillis() / 1000 - seconds;
        return then + "." + sign(Long.toString(then));
    }
}
