package io.sharpen.web;

import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.UsageSession;
import io.sharpen.service.SessionService.SessionInput;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/** The log / rate form. Deliberately short: a session should take fifteen seconds to record. */
public class SessionForm {

    @NotNull @PastOrPresent private LocalDate occurredOn = LocalDate.now();
    @NotNull private UsageContext context = UsageContext.PROFESSIONAL;
    @NotBlank @Size(max = 60) private String tool = "Claude";
    @NotNull private TaskCategory taskCategory = TaskCategory.CODING;
    @Min(0) @Max(1440) private int durationMinutes = 30;
    @Min(0) @Max(2000) private int promptCount = 5;
    @Min(0) @Max(100) private int humanContributionPct = 50;
    private boolean verifiedOutput = true;
    private boolean learnedSomething = false;
    @Min(1) @Max(5) private int outcome = 4;
    @Size(max = 500) private String notes;

    public static SessionForm from(UsageSession s) {
        SessionForm f = new SessionForm();
        f.occurredOn = s.getOccurredOn();
        f.context = s.getContext();
        f.tool = s.getTool();
        f.taskCategory = s.getTaskCategory();
        f.durationMinutes = s.getDurationMinutes();
        f.promptCount = s.getPromptCount();
        f.humanContributionPct = s.getHumanContributionPct();
        f.verifiedOutput = s.isVerifiedOutput();
        f.learnedSomething = s.isLearnedSomething();
        f.outcome = s.getOutcome();
        f.notes = s.getNotes();
        return f;
    }

    public SessionInput toInput() {
        return new SessionInput(occurredOn, context, tool, taskCategory, durationMinutes, promptCount,
                humanContributionPct, verifiedOutput, learnedSomething, outcome, notes, null, null, null);
    }

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
    public void setHumanContributionPct(int humanContributionPct) { this.humanContributionPct = humanContributionPct; }
    public boolean isVerifiedOutput() { return verifiedOutput; }
    public void setVerifiedOutput(boolean verifiedOutput) { this.verifiedOutput = verifiedOutput; }
    public boolean isLearnedSomething() { return learnedSomething; }
    public void setLearnedSomething(boolean learnedSomething) { this.learnedSomething = learnedSomething; }
    public int getOutcome() { return outcome; }
    public void setOutcome(int outcome) { this.outcome = outcome; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
