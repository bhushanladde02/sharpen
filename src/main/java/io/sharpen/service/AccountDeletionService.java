package io.sharpen.service;

import io.sharpen.domain.Person;
import io.sharpen.repo.FeedbackRepository;
import io.sharpen.repo.MonthlyReportRepository;
import io.sharpen.repo.PersonAvatarRepository;
import io.sharpen.repo.PersonRepository;
import io.sharpen.repo.UsageSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes an account and everything that belongs to it, in one transaction, after the person has proved it is
 * them by re-entering their password. Sessions, generated reports and the picture go with the account; feedback
 * they left stays (it is the site's record, not theirs) but is unlinked from the person. Nothing is soft-deleted
 * or kept "for 30 days" — the person asked for it to be gone, so it is gone, and the public profile URL answers
 * 404 from the next request. The PostgreSQL schema would cascade most of this on its own; doing it explicitly
 * means H2 in development behaves identically and the test can count what is left.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final PersonRepository people;
    private final UsageSessionRepository sessions;
    private final MonthlyReportRepository reports;
    private final PersonAvatarRepository avatars;
    private final FeedbackRepository feedback;
    private final PasswordEncoder passwordEncoder;
    private final CommunityService community;

    public AccountDeletionService(PersonRepository people, UsageSessionRepository sessions, MonthlyReportRepository reports,
                                  PersonAvatarRepository avatars, FeedbackRepository feedback, PasswordEncoder passwordEncoder,
                                  CommunityService community) {
        this.people = people;
        this.sessions = sessions;
        this.reports = reports;
        this.avatars = avatars;
        this.feedback = feedback;
        this.passwordEncoder = passwordEncoder;
        this.community = community;
    }

    /** True when the password matches — the only key that unlocks deletion. */
    public boolean confirms(Person person, String rawPassword) {
        return rawPassword != null && passwordEncoder.matches(rawPassword, person.getPasswordHash());
    }

    /** Removes the account and its data. Returns how many sessions and reports went with it, for the log line. */
    @Transactional
    public Removed delete(Person person) {
        Long id = person.getId();
        long s = sessions.deleteByPersonId(id);
        long r = reports.deleteByPersonId(id);
        avatars.deleteById(id);
        for (var f : feedback.findByPersonId(id)) f.detachPerson();   // keep the message, drop the link
        people.deleteById(id);
        community.invalidate();
        log.info("Account deleted: handle={} sessions={} reports={}", person.getHandle(), s, r);
        return new Removed(s, r);
    }

    public record Removed(long sessions, long reports) {}
}
