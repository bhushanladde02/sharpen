package io.sharpen.service;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Feedback;
import io.sharpen.repo.FeedbackRepository;
import io.sharpen.repo.MonthlyReportRepository;
import io.sharpen.repo.PersonRepository;
import io.sharpen.repo.UsageSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Public counters and feedback. The counters are read on every page, so they are cached for a few seconds:
 * four COUNT(*) queries per page view would be silly, four per ten seconds is nothing.
 */
@Service
public class CommunityService {

    private static final long TTL_MS = 10_000;

    private final PersonRepository people;
    private final UsageSessionRepository sessions;
    private final MonthlyReportRepository reports;
    private final FeedbackRepository feedback;

    private volatile CommunityStats cached = new CommunityStats(0, 0, 0, 0);
    private volatile long cachedAt = 0;

    public CommunityService(PersonRepository people, UsageSessionRepository sessions,
                            MonthlyReportRepository reports, FeedbackRepository feedback) {
        this.people = people;
        this.sessions = sessions;
        this.reports = reports;
        this.feedback = feedback;
    }

    @Transactional(readOnly = true)
    public CommunityStats stats() {
        long now = System.currentTimeMillis();
        if (now - cachedAt > TTL_MS) {
            cached = new CommunityStats(people.countByAccountType(AccountType.INDIVIDUAL),
                    people.countByAccountType(AccountType.COMPANY), sessions.count(), reports.count());
            cachedAt = now;
        }
        return cached;
    }

    /** Called after a registration so the counter on the next page load is exact, not up to 10 s stale. */
    public void invalidate() { cachedAt = 0; }

    @Transactional
    public Feedback leave(Long personId, String email, Integer rating, String message, String page) {
        return feedback.save(new Feedback(personId, blank(email), rating, message.trim(), blank(page)));
    }

    @Transactional(readOnly = true)
    public List<Feedback> latestFeedback() { return feedback.findTop200ByOrderByCreatedAtDesc(); }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
