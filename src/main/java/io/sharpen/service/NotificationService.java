package io.sharpen.service;

import io.sharpen.domain.Notification;
import io.sharpen.domain.Person;
import io.sharpen.repo.NotificationRepository;
import io.sharpen.repo.PersonRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * In-app notifications, deliberately basic: a few events that someone would act on, shown as a count in the menu
 * and a list at {@code /notifications}. No email, no push, no preferences page. The events: a welcome on sign-up,
 * a friend joining from your invitation link, a monthly report frozen by the scheduler — and, for the owner's
 * account, a new member and a new feedback message. The same event for the same link is never written twice, so
 * the report catch-up that runs daily cannot repeat itself.
 */
@Service
public class NotificationService {

    public static final String WELCOME = "welcome", FRIEND_JOINED = "friend-joined", REPORT_READY = "report-ready",
            NEW_MEMBER = "new-member", NEW_FEEDBACK = "new-feedback";

    private final NotificationRepository notifications;
    private final PersonRepository people;
    private final String adminEmail;

    public NotificationService(NotificationRepository notifications, PersonRepository people,
                               @Value("${sharpen.admin-email:}") String adminEmail) {
        this.notifications = notifications;
        this.people = people;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
    }

    /** Writes one notification unless the same kind for the same link already exists for this person. */
    @Transactional
    public void notify(Long personId, String kind, String title, String body, String link) {
        if (link != null && notifications.existsByPersonIdAndKindAndLink(personId, kind, link)) return;
        notifications.save(new Notification(personId, kind, cut(title, 200), body == null ? null : cut(body, 500), link));
    }

    /** The owner's account, if {@code sharpen.admin-email} names one; {@code except} is not told about their own doing. */
    @Transactional
    public void notifyAdmin(String kind, String title, String body, String link, Long except) {
        if (adminEmail.isEmpty()) return;
        people.findByEmailIgnoreCase(adminEmail).map(Person::getId).filter(id -> !id.equals(except))
                .ifPresent(id -> notifications.save(new Notification(id, kind, cut(title, 200), body == null ? null : cut(body, 500), link)));
    }

    @Transactional(readOnly = true)
    public long unread(Person person) { return notifications.countByPersonIdAndReadAtIsNull(person.getId()); }

    /** The newest fifty, unread first by date order as written. */
    @Transactional(readOnly = true)
    public List<Notification> latest(Person person) { return notifications.findTop50ByPersonIdOrderByCreatedAtDesc(person.getId()); }

    /** Opening one marks it read and says where it points; empty if it is not this person's. */
    @Transactional
    public Optional<String> open(Long id, Person person) {
        return notifications.findByIdAndPersonId(id, person.getId()).map(n -> {
            if (n.isUnread()) { n.setReadAt(Instant.now()); notifications.save(n); }
            return n.getLink() == null ? "/notifications" : n.getLink();
        });
    }

    @Transactional
    public int markAllRead(Person person) { return notifications.markAllRead(person.getId(), Instant.now()); }

    /** When an account is deleted its notifications go with it. */
    @Transactional
    public void forget(Long personId) { notifications.deleteByPersonId(personId); }

    private static String cut(String s, int max) { return s.length() > max ? s.substring(0, max) : s; }
}
