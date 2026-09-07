package io.sharpen.domain;

import io.sharpen.domain.Enums.SessionSource;
import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One block of AI usage: a chat, a coding session, an hour with Copilot.
 * The self-assessment fields (human contribution, verified, learned, outcome) are what the AI score is built from;
 * volume fields (minutes, prompts, tokens) describe usage but never raise the score on their own.
 */
@Entity
@Table(name = "usage_session", indexes = {
        @Index(name = "ix_session_person_date", columnList = "person_id, occurred_on"),
        @Index(name = "ux_session_person_external", columnList = "person_id, external_id", unique = true)
})
public class UsageSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UsageContext context;

    @Column(nullable = false, length = 60)
    private String tool;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_category", nullable = false, length = 20)
    private TaskCategory taskCategory;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "prompt_count", nullable = false)
    private int promptCount;

    /** 0-100: how much of the finished work came from the person rather than the model. */
    @Column(name = "human_contribution_pct", nullable = false)
    private int humanContributionPct;

    /** The person checked the AI's output against a source, a test, or their own judgement. */
    @Column(name = "verified_output", nullable = false)
    private boolean verifiedOutput;

    /** The person can now do something they could not before the session. */
    @Column(name = "learned_something", nullable = false)
    private boolean learnedSomething;

    /** 1-5 how useful the result was. */
    @Column(nullable = false)
    private int outcome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionSource source;

    /** Set when the person has filled in the self-assessment; imports arrive with false. */
    @Column(name = "self_assessed", nullable = false)
    private boolean selfAssessed;

    /** Stable id from the extension or provider export, used to de-duplicate re-syncs. */
    @Column(name = "external_id", length = 120)
    private String externalId;

    @Column(name = "tokens_in")
    private Long tokensIn;

    @Column(name = "tokens_out")
    private Long tokensOut;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected UsageSession() {}

    public UsageSession(Person person, LocalDate occurredOn, UsageContext context, String tool, TaskCategory taskCategory) {
        this.person = person;
        this.occurredOn = occurredOn;
        this.context = context;
        this.tool = tool;
        this.taskCategory = taskCategory;
        this.source = SessionSource.MANUAL;
        this.humanContributionPct = 50;
        this.outcome = 3;
    }

    public Long getId() { return id; }
    public Person getPerson() { return person; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public void setOccurredOn(LocalDate occurredOn) { this.occurredOn = occurredOn; }
    public UsageContext getContext() { return context; }
    public void setContext(UsageContext context) { this.context = context; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public TaskCategory getTaskCategory() { return taskCategory; }
    public void setTaskCategory(TaskCategory taskCategory) { this.taskCategory = taskCategory; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public int getPromptCount() { return promptCount; }
    public void setPromptCount(int promptCount) { this.promptCount = promptCount; }
    public int getHumanContributionPct() { return humanContributionPct; }
    public void setHumanContributionPct(int humanContributionPct) { this.humanContributionPct = clamp(humanContributionPct, 0, 100); }
    public boolean isVerifiedOutput() { return verifiedOutput; }
    public void setVerifiedOutput(boolean verifiedOutput) { this.verifiedOutput = verifiedOutput; }
    public boolean isLearnedSomething() { return learnedSomething; }
    public void setLearnedSomething(boolean learnedSomething) { this.learnedSomething = learnedSomething; }
    public int getOutcome() { return outcome; }
    public void setOutcome(int outcome) { this.outcome = clamp(outcome, 1, 5); }
    public SessionSource getSource() { return source; }
    public void setSource(SessionSource source) { this.source = source; }
    public boolean isSelfAssessed() { return selfAssessed; }
    public void setSelfAssessed(boolean selfAssessed) { this.selfAssessed = selfAssessed; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public Long getTokensIn() { return tokensIn; }
    public void setTokensIn(Long tokensIn) { this.tokensIn = tokensIn; }
    public Long getTokensOut() { return tokensOut; }
    public void setTokensOut(Long tokensOut) { this.tokensOut = tokensOut; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
