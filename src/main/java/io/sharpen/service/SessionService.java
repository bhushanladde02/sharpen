package io.sharpen.service;

import io.sharpen.domain.Enums.SessionSource;
import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.Person;
import io.sharpen.domain.UsageSession;
import io.sharpen.repo.UsageSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SessionService {

    /** A session as submitted by a form, the extension, or an import. Null fields keep existing values on update. */
    public record SessionInput(
            LocalDate occurredOn,
            UsageContext context,
            String tool,
            TaskCategory taskCategory,
            Integer durationMinutes,
            Integer promptCount,
            Integer humanContributionPct,
            Boolean verifiedOutput,
            Boolean learnedSomething,
            Integer outcome,
            String notes,
            String externalId,
            Long tokensIn,
            Long tokensOut
    ) {}

    private final UsageSessionRepository sessions;

    public SessionService(UsageSessionRepository sessions) {
        this.sessions = sessions;
    }

    /** Create from a form: the person filled in every self-assessment field. */
    public UsageSession logManual(Person person, SessionInput in) {
        UsageSession s = new UsageSession(person, in.occurredOn(), in.context(), clean(in.tool()), in.taskCategory());
        apply(s, in);
        s.setSource(SessionSource.MANUAL);
        s.setSelfAssessed(true);
        return sessions.save(s);
    }

    /**
     * Upsert by external id for the extension and importers. Re-syncing the same session updates volume fields
     * but never overwrites a self-assessment the person has already entered.
     */
    public record Upsert(UsageSession session, boolean created) {}

    public Upsert upsertExternal(Person person, SessionInput in, SessionSource source) {
        Optional<UsageSession> existing = in.externalId() == null ? Optional.empty()
                : sessions.findByPersonIdAndExternalId(person.getId(), in.externalId());
        UsageSession s = existing.orElseGet(() ->
                new UsageSession(person, in.occurredOn(), in.context(),  clean(in.tool()),
                        in.taskCategory() == null ? TaskCategory.OTHER : in.taskCategory()));
        if (existing.isPresent() && s.isSelfAssessed()) {
            applyVolumeOnly(s, in);
        } else {
            apply(s, in);
            // An import counts as assessed only when the source supplied every assessment field.
            s.setSelfAssessed(in.humanContributionPct() != null && in.verifiedOutput() != null
                    && in.learnedSomething() != null && in.outcome() != null);
        }
        s.setSource(source);
        s.setExternalId(in.externalId());
        return new Upsert(sessions.save(s), existing.isEmpty());
    }

    /** The person rates a session that arrived from an import or the extension. */
    public UsageSession assess(Person person, Long id, SessionInput in) {
        UsageSession s = sessions.findByIdAndPersonId(id, person.getId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        apply(s, in);
        s.setSelfAssessed(true);
        return sessions.save(s);
    }

    public void delete(Person person, Long id) {
        sessions.findByIdAndPersonId(id, person.getId()).ifPresent(sessions::delete);
    }

    @Transactional(readOnly = true)
    public Optional<UsageSession> find(Person person, Long id) {
        return sessions.findByIdAndPersonId(id, person.getId());
    }

    @Transactional(readOnly = true)
    public Page<UsageSession> page(Person person, int page, int size) {
        return sessions.findByPersonIdOrderByOccurredOnDescIdDesc(person.getId(), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<UsageSession> inMonth(Person person, YearMonth month) {
        return sessions.findByPersonIdAndOccurredOnBetweenOrderByOccurredOnDesc(
                person.getId(), month.atDay(1), month.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public List<UsageSession> between(Person person, LocalDate from, LocalDate to) {
        return sessions.findByPersonIdAndOccurredOnBetweenOrderByOccurredOnDesc(person.getId(), from, to);
    }

    @Transactional(readOnly = true)
    public List<UsageSession> needingAssessment(Person person, int limit) {
        return sessions.findByPersonIdAndSelfAssessedFalseOrderByOccurredOnDesc(person.getId(), PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public long countNeedingAssessment(Person person) {
        return sessions.countByPersonIdAndSelfAssessedFalse(person.getId());
    }

    @Transactional(readOnly = true)
    public long count(Person person) { return sessions.countByPersonId(person.getId()); }

    @Transactional(readOnly = true)
    public Optional<LocalDate> firstSessionDate(Person person) { return sessions.firstSessionDate(person.getId()); }

    private static void apply(UsageSession s, SessionInput in) {
        if (in.occurredOn() != null) s.setOccurredOn(in.occurredOn());
        if (in.context() != null) s.setContext(in.context());
        if (in.tool() != null && !in.tool().isBlank()) s.setTool(clean(in.tool()));
        if (in.taskCategory() != null) s.setTaskCategory(in.taskCategory());
        applyVolumeOnly(s, in);
        if (in.humanContributionPct() != null) s.setHumanContributionPct(in.humanContributionPct());
        if (in.verifiedOutput() != null) s.setVerifiedOutput(in.verifiedOutput());
        if (in.learnedSomething() != null) s.setLearnedSomething(in.learnedSomething());
        if (in.outcome() != null) s.setOutcome(in.outcome());
        if (in.notes() != null) s.setNotes(in.notes().isBlank() ? null : in.notes().trim());
    }

    private static void applyVolumeOnly(UsageSession s, SessionInput in) {
        if (in.durationMinutes() != null) s.setDurationMinutes(Math.max(0, in.durationMinutes()));
        if (in.promptCount() != null) s.setPromptCount(Math.max(0, in.promptCount()));
        if (in.tokensIn() != null) s.setTokensIn(in.tokensIn());
        if (in.tokensOut() != null) s.setTokensOut(in.tokensOut());
    }

    static String clean(String tool) {
        return tool == null || tool.isBlank() ? "Other" : tool.trim();
    }
}
