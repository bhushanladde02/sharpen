package io.sharpen.service;

import io.sharpen.domain.Invite;
import io.sharpen.domain.MailOptOut;
import io.sharpen.domain.Person;
import io.sharpen.repo.InviteRepository;
import io.sharpen.repo.MailOptOutRepository;
import io.sharpen.repo.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * "Invite a friend". The invitation is a short, honest text that a member can send however they like — their own
 * mail app (a {@code mailto:} link), the clipboard, LinkedIn, WhatsApp, X — and, when the deployment has mail
 * switched on, can ask Sharpen to send as a proper HTML email. Every route carries the member's personal link
 * {@code /join/<handle>}, so an account made from it is counted as a friend who joined.
 *
 * <p>Sending on someone's behalf is the one feature here that could annoy strangers, so it is kept narrow: only a
 * signed-in member can ask; at most {@link #PER_DAY} a day per member and {@code sharpen.mail.daily-limit} for the
 * whole site; one email per address, ever; never to an address that already has an account or that asked not to
 * be written to; and the address itself is not kept — only a one-way hash, so the rules above can be applied.
 */
@Service
public class InviteService {

    private static final Logger log = LoggerFactory.getLogger(InviteService.class);

    public static final int PER_DAY = 10;
    public static final int NOTE_MAX = 300;

    /** Why a send was refused, in words meant for the inviter. */
    public enum Refusal {
        MAIL_OFF("Sending from Sharpen is not switched on here — use your own mail app or copy the invitation."),
        BAD_ADDRESS("That does not look like an email address."),
        ALREADY_MEMBER("That address already has a Sharpen account — you could send them your profile instead."),
        CANNOT_SEND("Sharpen cannot email that address — it has been invited before, or asked not to be. Your link works anywhere, though."),
        LIMIT("You have sent " + PER_DAY + " invitations today — the most Sharpen will send for one person in a day. Your link works anywhere, though."),
        SITE_LIMIT("Sharpen has sent as many invitations as it will today. Try again tomorrow, or use your own mail app."),
        FAILED("The email could not be sent just now. Nothing was recorded; please try again in a few minutes.");

        public final String message;
        Refusal(String message) { this.message = message; }
    }

    /**
     * The invitation as text, in the pieces the page rebuilds live as the member types: greeting, the fixed
     * introduction, the member's own line (may be empty) and the fixed tail with the links. {@link #body()} joins them.
     */
    public record Text(String subject, String greeting, String intro, String note, String tail) {
        public String body() {
            return greeting + "\n\n" + intro + (note.isEmpty() ? "" : "\n\n" + note) + "\n\n" + tail;
        }
    }

    private final InviteRepository invites;
    private final MailOptOutRepository optOuts;
    private final PersonRepository people;
    private final Mailer mailer;
    private final TemplateEngine templates;
    private final NotificationService notifications;
    private final String base;
    private final int siteDailyLimit;
    private final SecureRandom random = new SecureRandom();

    public InviteService(InviteRepository invites, MailOptOutRepository optOuts, PersonRepository people, Mailer mailer,
                         TemplateEngine templates, NotificationService notifications,
                         @Value("${sharpen.site-host:localhost:8080}") String siteHost,
                         @Value("${sharpen.mail.daily-limit:80}") int siteDailyLimit) {
        this.invites = invites;
        this.optOuts = optOuts;
        this.people = people;
        this.mailer = mailer;
        this.templates = templates;
        this.notifications = notifications;
        this.base = (siteHost.startsWith("localhost") || siteHost.startsWith("127.") ? "http://" : "https://") + siteHost;
        this.siteDailyLimit = siteDailyLimit;
    }

    public boolean mailEnabled() { return mailer.enabled(); }

    /** The member's personal link; an account created from it is counted as their invitee. */
    public String joinLink(Person inviter) { return base + "/join/" + inviter.getHandle(); }

    /** The invitation in plain words. {@code friend} and {@code note} may be blank. */
    public Text text(Person inviter, String friend, String note) {
        String name = inviter.getDisplayName();
        String greeting = friend == null || friend.isBlank() ? "Hi," : "Hi " + clean(friend) + ",";
        String intro = name + " is using Sharpen and thought you might like it too.\n\n"
                + "Sharpen is a free, open-source place to keep an honest record of how you work with AI — not how much, "
                + "but how well: whether the thinking is yours, whether you check what the model gives you, whether you are "
                + "learning or just getting things done. Logging a session takes fifteen seconds; a monthly report and an "
                + "AI score follow.";
        String tail = "Join here: " + joinLink(inviter) + "\n"
                + "Or try the free 60-second check first, no account needed: " + base + "/check\n\n"
                + "Free, no ads, your data is yours to export or delete, and your profile stays private unless you choose to share it.";
        return new Text(name + " invited you to Sharpen", greeting, intro, note == null ? "" : clean(note), tail);
    }

    /** The clipboard / social version: the text with the sign-off a message needs. */
    public String shareText(Person inviter) {
        return "I keep an honest record of how I work with AI on Sharpen — free and open source. Join me: " + joinLink(inviter);
    }

    /**
     * Sends the invitation as an email from Sharpen, after every check. Returns empty on success. The address is
     * hashed before anything is stored; the only place it exists in clear is the message itself.
     */
    @Transactional
    public Optional<Refusal> send(Person inviter, String email, String friend, String note) {
        if (!mailer.enabled()) return Optional.of(Refusal.MAIL_OFF);
        String to = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!looksLikeAddress(to)) return Optional.of(Refusal.BAD_ADDRESS);
        if (people.findByEmailIgnoreCase(to).isPresent()) return Optional.of(Refusal.ALREADY_MEMBER);
        String hash = hash(to);
        if (optOuts.existsById(hash) || invites.findByEmailHash(hash).isPresent()) return Optional.of(Refusal.CANNOT_SEND);
        Instant dayAgo = Instant.now().minus(Duration.ofDays(1));
        if (invites.countByInviterIdAndSentAtAfter(inviter.getId(), dayAgo) >= PER_DAY) return Optional.of(Refusal.LIMIT);
        if (invites.countBySentAtAfter(dayAgo) >= siteDailyLimit) return Optional.of(Refusal.SITE_LIMIT);

        String token = newToken();
        String unsubscribe = base + "/invite/unsubscribe/" + token;
        Text text = text(inviter, friend, note);
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("inviter", inviter.getDisplayName());
        ctx.setVariable("friend", friend == null ? "" : friend.trim());
        ctx.setVariable("note", note == null ? "" : clean(note));
        ctx.setVariable("joinUrl", joinLink(inviter));
        ctx.setVariable("checkUrl", base + "/check");
        ctx.setVariable("siteUrl", base);
        ctx.setVariable("unsubscribeUrl", unsubscribe);
        String html = templates.process("mail/invite", ctx);
        String plain = text.body() + "\n\n—\nYou received this one email because " + inviter.getDisplayName()
                + " asked Sharpen to invite you. Sharpen will not write to this address again unless you join. "
                + "Stop any further invitations: " + unsubscribe;
        try {
            mailer.send(to, text.subject(), html, plain, unsubscribe);
        } catch (IOException e) {
            log.warn("Invitation from {} not sent: {}", inviter.getHandle(), e.getMessage());
            return Optional.of(Refusal.FAILED);
        }
        invites.save(new Invite(inviter.getId(), hash, token));
        log.info("Invitation sent on behalf of {}", inviter.getHandle());
        return Optional.empty();
    }

    /** The unsubscribe link: the address behind the token will never be written to again. True if the token was real. */
    @Transactional
    public boolean unsubscribe(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{20,40}")) return false;
        Optional<Invite> invite = invites.findByToken(token);
        if (invite.isEmpty()) return false;
        if (!optOuts.existsById(invite.get().getEmailHash())) optOuts.save(new MailOptOut(invite.get().getEmailHash()));
        return true;
    }

    /**
     * A new account: remember who invited them (from the {@code /join/<handle>} link, if that is where they came
     * from) and, if Sharpen emailed this address, mark that invitation accepted.
     */
    @Transactional
    public void joined(Person created, String refHandle) {
        if (refHandle != null && !refHandle.isBlank()) {
            people.findByHandle(refHandle.trim()).filter(p -> !p.getId().equals(created.getId())).ifPresent(inviter -> {
                created.setInvitedBy(inviter.getId());
                people.save(created);
                notifications.notify(inviter.getId(), NotificationService.FRIEND_JOINED, created.getDisplayName() + " joined from your invitation",
                        "They created an account from your link — that is one more person keeping an honest record.", "/invite");
            });
        }
        invites.findByEmailHash(hash(created.getEmail())).ifPresent(i -> { if (i.getAcceptedAt() == null) { i.setAcceptedAt(Instant.now()); invites.save(i); } });
    }

    /** What to show the inviter: how many people joined from their link, how many emails Sharpen sent for them. */
    public record Tally(long joined, long emailed, long emailedToday) {}

    @Transactional(readOnly = true)
    public Tally tally(Person inviter) {
        return new Tally(people.countByInvitedBy(inviter.getId()), invites.countByInviterId(inviter.getId()),
                invites.countByInviterIdAndSentAtAfter(inviter.getId(), Instant.now().minus(Duration.ofDays(1))));
    }

    /** When a member deletes their account their invitations go too; the people they invited stay, unlinked. */
    @Transactional
    public void forget(Long personId) {
        invites.deleteByInviterId(personId);
        people.clearInvitedBy(personId);
    }

    /**
     * The loose shape of an address — something, one {@code @}, a domain with a dot — checked with index
     * arithmetic rather than a pattern, so no input can make it slow (CodeQL on PR #66). Delivery decides the rest.
     */
    public static boolean looksLikeAddress(String s) {
        if (s.isEmpty() || s.length() > 190) return false;
        int at = s.indexOf('@');
        if (at < 1 || at != s.lastIndexOf('@')) return false;
        int dot = s.lastIndexOf('.');
        if (dot < at + 2 || dot == s.length() - 1) return false;
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i)) || Character.isISOControl(s.charAt(i))) return false;
        return true;
    }

    /** SHA-256 of the lower-cased address — the only form in which an invitee's address is stored. */
    public static String hash(String email) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String newToken() {
        byte[] b = new byte[24];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /**
     * A personal note is plain text: one paragraph, at most {@link #NOTE_MAX} characters, every run of whitespace
     * or control characters folded to one space. A single pass over the characters — no pattern — so the length of
     * the input is the only cost (CodeQL on PR #66); the input is cut to a bounded size first.
     */
    public static String clean(String note) {
        String in = note.length() > NOTE_MAX * 4 ? note.substring(0, NOTE_MAX * 4) : note;
        StringBuilder out = new StringBuilder(Math.min(in.length(), NOTE_MAX));
        boolean space = false;
        for (int i = 0; i < in.length() && out.length() < NOTE_MAX; i++) {
            char c = in.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) { space = out.length() > 0; continue; }
            if (space) { out.append(' '); space = false; }
            out.append(c);
        }
        return out.toString();
    }
}
