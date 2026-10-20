package io.sharpen.service;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Feedback;
import io.sharpen.repo.FeedbackRepository;
import io.sharpen.repo.MonthlyReportRepository;
import io.sharpen.repo.PageViewRepository;
import io.sharpen.repo.PersonRepository;
import io.sharpen.repo.UsageSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Public counters and feedback. The counters are read on every page, so they are cached: the account counts
 * for ten seconds (four COUNT(*) queries per page view would be silly, four per ten seconds is nothing), the
 * traffic totals — which scan the page-view table — for a minute.
 */
@Service
public class CommunityService {

    private static final long TTL_MS = 10_000;
    private static final long TRAFFIC_TTL_MS = 60_000;

    private final PersonRepository people;
    private final UsageSessionRepository sessions;
    private final MonthlyReportRepository reports;
    private final FeedbackRepository feedback;
    private final PageViewRepository views;
    private final NotificationService notifications;

    private volatile CommunityStats cached = new CommunityStats(0, 0, 0, 0, 0, 0, 0);
    private volatile long cachedAt = 0;
    private volatile long[] traffic = new long[3];   // visits, page views, countries
    private volatile long trafficAt = 0;

    public CommunityService(PersonRepository people, UsageSessionRepository sessions,
                            MonthlyReportRepository reports, FeedbackRepository feedback, PageViewRepository views,
                            NotificationService notifications) {
        this.notifications = notifications;
        this.people = people;
        this.sessions = sessions;
        this.reports = reports;
        this.feedback = feedback;
        this.views = views;
    }

    @Transactional(readOnly = true)
    public CommunityStats stats() {
        long now = System.currentTimeMillis();
        if (now - trafficAt > TRAFFIC_TTL_MS) {
            Object[] all = views.allTime().get(0);
            traffic = new long[]{((Number) all[1]).longValue(), ((Number) all[0]).longValue(), views.countriesReached()};
            trafficAt = now;
            cachedAt = 0;
        }
        if (now - cachedAt > TTL_MS) {
            long[] t = traffic;
            cached = new CommunityStats(people.countByAccountType(AccountType.INDIVIDUAL),
                    people.countByAccountType(AccountType.COMPANY), sessions.count(), reports.count(), t[0], t[1], t[2]);
            cachedAt = now;
        }
        return cached;
    }

    /** Called after a registration so the counter on the next page load is exact, not up to 10 s stale. */
    public void invalidate() { cachedAt = 0; trafficAt = 0; }

    @Transactional
    public Feedback leave(Long personId, String email, Integer rating, String message, String page) {
        Feedback saved = feedback.save(new Feedback(personId, blank(email), rating, message.trim(), blank(page)));
        String first = message.trim().length() > 120 ? message.trim().substring(0, 120) + "…" : message.trim();
        notifications.notifyAdmin(NotificationService.NEW_FEEDBACK, "New feedback" + (rating != null ? " · " + rating + "/5" : ""),
                first, "/admin/feedback", personId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Feedback> latestFeedback() { return feedback.findTop200ByOrderByCreatedAtDesc(); }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
