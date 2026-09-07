package io.sharpen.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.MonthlyReport;
import io.sharpen.domain.Person;
import io.sharpen.domain.UsageSession;
import io.sharpen.repo.MonthlyReportRepository;
import io.sharpen.repo.PersonRepository;
import io.sharpen.scoring.AiScore;
import io.sharpen.service.ReportModel.BrainActive;
import io.sharpen.service.ReportModel.Delta;
import io.sharpen.service.ReportModel.Insight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds, stores and re-reads monthly reports. Generation is idempotent per person and month. */
@Service
@Transactional
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final MonthlyReportRepository reports;
    private final PersonRepository people;
    private final StatsService stats;
    private final SessionService sessions;
    private final ObjectMapper json;

    public ReportService(MonthlyReportRepository reports, PersonRepository people, StatsService stats,
                         SessionService sessions, ObjectMapper json) {
        this.reports = reports;
        this.people = people;
        this.stats = stats;
        this.sessions = sessions;
        this.json = json;
    }

    /** Runs at 02:00 UTC on the first of each month and freezes the month that just ended for every individual. */
    @Scheduled(cron = "0 0 2 1 * *", zone = "UTC")
    public void generateLastMonthForEveryone() {
        YearMonth last = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        int n = 0;
        for (Person p : people.findByAccountType(AccountType.INDIVIDUAL)) {
            if (!sessions.inMonth(p, last).isEmpty()) { generate(p, last); n++; }
        }
        log.info("Generated {} monthly reports for {}", n, last);
    }

    public MonthlyReport generate(Person person, YearMonth month) {
        ReportModel model = build(person, month);
        MonthlyReport report = reports.findByPersonIdAndYearMonth(person.getId(), month.toString())
                .orElseGet(() -> new MonthlyReport(person, month.toString()));
        AiScore score = model.month().score();
        report.setAiScore(score.composite());
        report.setIndependence(score.independence());
        report.setEffectiveness(score.effectiveness());
        report.setVerification(score.verification());
        report.setGrowth(score.growth());
        report.setBreadth(score.breadth());
        report.setSessionCount(model.month().sessions());
        report.setTotalMinutes(model.month().minutes());
        report.setGeneratedAt(Instant.now());
        try {
            report.setPayload(json.writeValueAsString(model));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise report", e);
        }
        return reports.save(report);
    }

    @Transactional(readOnly = true)
    public Optional<ReportModel> stored(Person person, YearMonth month) {
        return reports.findByPersonIdAndYearMonth(person.getId(), month.toString()).map(this::read);
    }

    @Transactional(readOnly = true)
    public List<MonthlyReport> history(Person person) {
        return reports.findByPersonIdOrderByYearMonthDesc(person.getId());
    }

    public ReportModel read(MonthlyReport report) {
        try {
            return json.readValue(report.getPayload(), ReportModel.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored report is unreadable", e);
        }
    }

    /** Builds the report live, without storing it. */
    @Transactional(readOnly = true)
    public ReportModel build(Person person, YearMonth month) {
        MonthSummary current = stats.month(person, month);
        MonthSummary previous = stats.month(person, month.minusMonths(1));
        boolean hasPrev = previous.sessions() > 0;
        AiScore s = current.score(), p = previous.score();

        Delta delta = hasPrev && s.hasScore() && p.hasScore()
                ? new Delta(s.composite() - p.composite(), s.independence() - p.independence(),
                s.effectiveness() - p.effectiveness(), s.verification() - p.verification(),
                s.growth() - p.growth(), s.breadth() - p.breadth(),
                current.minutes() - previous.minutes(), current.sessions() - previous.sessions())
                : new Delta(null, null, null, null, null, null,
                hasPrev ? current.minutes() - previous.minutes() : null,
                hasPrev ? current.sessions() - previous.sessions() : null);

        BrainActive brain = brainActive(sessions.inMonth(person, month), s);
        List<Insight> insights = insights(current, previous, hasPrev);
        List<String> next = nextMonth(s, brain);

        return new ReportModel(person.getDisplayName(), person.getHandle(), person.getHeadline(),
                month.toString(), current.label(), Instant.now().toString(),
                current, hasPrev ? previous : null, delta, brain, insights, next);
    }

    static BrainActive brainActive(List<UsageSession> list, AiScore score) {
        int humanMinutes = 0, total = 0, verified = 0, learned = 0, assessed = 0;
        for (UsageSession x : list) {
            if (!x.isSelfAssessed()) continue;
            assessed++;
            total += x.getDurationMinutes();
            humanMinutes += Math.round(x.getDurationMinutes() * x.getHumanContributionPct() / 100f);
            if (x.isVerifiedOutput()) verified++;
            if (x.isLearnedSomething()) learned++;
        }
        if (assessed == 0) return new BrainActive(0, 0, 0, 0, 0, "No rated sessions this month.");
        int humanPct = total == 0 ? 0 : Math.round(100f * humanMinutes / total);
        String verdict;
        if (humanPct >= 60 && verified * 100 / assessed >= 60) verdict = "Sharp: you did most of the thinking and checked the rest.";
        else if (humanPct >= 40) verdict = "Balanced: a real collaboration, with room to take the first pass yourself more often.";
        else verdict = "Leaning on the model: most of this month's output was generated, not authored. Reclaim one task type.";
        return new BrainActive(humanPct, 100 * verified / assessed, 100 * learned / assessed,
                humanMinutes, total - humanMinutes, verdict);
    }

    static List<Insight> insights(MonthSummary cur, MonthSummary prev, boolean hasPrev) {
        List<Insight> out = new ArrayList<>();
        AiScore s = cur.score();
        if (cur.sessions() == 0) {
            out.add(new Insight("empty", "Nothing logged", "No AI sessions were recorded this month."));
            return out;
        }
        if (hasPrev && s.hasScore() && prev.score().hasScore()) {
            int d = s.composite() - prev.score().composite();
            if (d >= 25) out.add(new Insight("up", "Score up " + d + " points", "Better than " + prev.label() + " on the dimensions you control."));
            else if (d <= -25) out.add(new Insight("down", "Score down " + Math.abs(d) + " points", "Check independence and verification below — those move the score most."));
        }
        if (!cur.tools().isEmpty()) {
            MonthSummary.Share t = cur.tools().get(0);
            out.add(new Insight("tool", t.name() + " carried " + t.pct() + "% of your AI time",
                    t.sessions() + " session" + (t.sessions() == 1 ? "" : "s") + ", " + t.minutes() + " minutes."));
        }
        if (!cur.categories().isEmpty()) {
            MonthSummary.Share c = cur.categories().get(0);
            out.add(new Insight("task", "Most AI time went to " + c.name().toLowerCase(),
                    c.pct() + "% of minutes. " + (cur.categories().size() >= 4 ? "Good spread across task types." : "Consider one more task type next month.")));
        }
        if (cur.professionalPct() >= 80) {
            out.add(new Insight("balance", "Almost all of it was work", cur.professionalPct() + "% professional. Personal use is where low-stakes experiments happen."));
        } else if (cur.personalPct() >= 80) {
            out.add(new Insight("balance", "Almost all of it was personal", cur.personalPct() + "% personal. Employers see the professional split on your profile."));
        }
        for (AiScore.Flag f : s.flags()) {
            if (!f.kind().equals("confidence")) out.add(new Insight(f.kind(), title(f.kind()), f.message()));
        }
        return out;
    }

    static List<String> nextMonth(AiScore s, BrainActive brain) {
        List<String> tips = new ArrayList<>();
        if (!s.hasScore()) {
            tips.add("Rate at least " + io.sharpen.scoring.AiScoreService.MIN_SESSIONS_FOR_FULL_CONFIDENCE + " sessions so the score has full confidence.");
            return tips;
        }
        if (s.independence() < 60) tips.add("Write the first draft yourself for 15 minutes before opening a chat, then use AI to review it.");
        if (s.verification() < 70) tips.add("End every session by checking one claim against a source or running one test.");
        if (s.growth() < 50) tips.add("Once a week, ask the model to explain its answer and redo the step by hand.");
        if (s.breadth() < 50) tips.add("Try AI on one task type you have not logged yet — planning or learning are good low-risk starts.");
        if (s.effectiveness() < 60) tips.add("Front-load context in the first prompt (goal, constraints, format) to cut prompt count.");
        if (brain.humanContributionPct() < 40) tips.add("Pick one recurring task and do it fully unassisted this month; log it with 100% human contribution.");
        if (tips.isEmpty()) tips.add("Keep the pattern: you lead, the model assists, you verify. Log the sessions so your profile stays current.");
        return tips.size() > 3 ? tips.subList(0, 3) : tips;
    }

    private static String title(String kind) {
        return switch (kind) {
            case "risk" -> "Reliance risk";
            case "verify" -> "Verification is low";
            case "growth" -> "Learning is low";
            case "strength" -> "Strength";
            case "breadth" -> "Narrow usage";
            case "assess" -> "Unrated sessions";
            default -> "Note";
        };
    }

    public static YearMonth parseMonth(String s) {
        return YearMonth.parse(s);
    }

    public static YearMonth lastCompleteMonth(LocalDate today) {
        return YearMonth.from(today).minusMonths(1);
    }
}
