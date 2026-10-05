package io.sharpen.service;

import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.repo.UsageSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The public "AI usage insights" page: what the whole community's rated sessions add up to over the last 90
 * days — hours per tool, work versus personal, how often output is checked, how much of the work stays human.
 * This is the measurement-panel idea done the honest way round: numbers are published about the aggregate,
 * never about a person, and only once enough people are in the aggregate for no one to be visible in it.
 *
 * Two thresholds keep it that way. Nothing is shown until {@link #MIN_PEOPLE} people have rated sessions in the
 * window; and a tool gets its own row only when {@link #MIN_PEOPLE_PER_TOOL} people used it, otherwise its
 * minutes fold into "Other tools". Only self-assessed sessions count — the same rule as the score — so an
 * import cannot move the community numbers any more than it can move an individual's.
 *
 * Computed at most once every fifteen minutes; the page is public and the queries touch every rated session.
 */
@Service
public class InsightsService {

    public static final int WINDOW_DAYS = 90;
    public static final int MIN_PEOPLE = 10;
    public static final int MIN_PEOPLE_PER_TOOL = 3;
    private static final long TTL_MS = 15 * 60_000;

    /** One bar: a name, its minutes, and its share of the whole (0–100). */
    public record Share(String name, long minutes, int percent, long people) {
        public long hours() { return Math.round(minutes / 60.0); }
    }

    public record Insights(LocalDate from, LocalDate to, long people, long sessions, long minutes,
                           int verifiedPercent, int learnedPercent, int humanPercent, double outcome,
                           int professionalPercent, int personalPercent,
                           List<Share> tools, List<Share> tasks) {
        public boolean published() { return people >= MIN_PEOPLE; }
        public long hours() { return Math.round(minutes / 60.0); }
        public int peopleToGo() { return (int) Math.max(0, MIN_PEOPLE - people); }
    }

    private final UsageSessionRepository sessions;
    private volatile Insights cached;
    private volatile long cachedAt;

    public InsightsService(UsageSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public Insights current() {
        long now = System.currentTimeMillis();
        Insights c = cached;
        if (c == null || now - cachedAt > TTL_MS) {
            c = compute(LocalDate.now());
            cached = c;
            cachedAt = now;
        }
        return c;
    }

    /** Called after an account is deleted so the aggregate forgets it on the next view rather than within the quarter-hour. */
    public void invalidate() { cachedAt = 0; }

    Insights compute(LocalDate today) {
        LocalDate from = today.minusDays(WINDOW_DAYS - 1);
        Object[] t = sessions.insightTotals(from).get(0);
        long people = num(t[0]), count = num(t[1]), minutes = num(t[2]), verified = num(t[3]), learned = num(t[4]);
        int humanPct = t[5] == null ? 0 : (int) Math.round(((Number) t[5]).doubleValue());
        double outcome = t[6] == null ? 0 : Math.round(((Number) t[6]).doubleValue() * 10) / 10.0;

        long pro = 0, personal = 0;
        for (Object[] r : sessions.insightContexts(from)) {
            if (r[0] == UsageContext.PROFESSIONAL) pro = num(r[1]); else personal = num(r[1]);
        }

        List<Share> tools = new ArrayList<>();
        long otherMinutes = 0, otherPeople = 0;
        for (Object[] r : sessions.insightTools(from)) {
            long m = num(r[1]), users = num(r[3]);
            if (users >= MIN_PEOPLE_PER_TOOL && tools.size() < 8) tools.add(new Share((String) r[0], m, pct(m, minutes), users));
            else { otherMinutes += m; otherPeople = Math.max(otherPeople, users); }
        }
        if (otherMinutes > 0) tools.add(new Share("Other tools", otherMinutes, pct(otherMinutes, minutes), otherPeople));

        List<Share> tasks = new ArrayList<>();
        for (Object[] r : sessions.insightTasks(from)) {
            long m = num(r[1]);
            if (m > 0) tasks.add(new Share(((TaskCategory) r[0]).label, m, pct(m, minutes), 0));
        }

        return new Insights(from, today, people, count, minutes,
                pct(verified, count), pct(learned, count), humanPct, outcome,
                pct(pro, minutes), pct(personal, minutes), tools, tasks);
    }

    private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }

    private static int pct(long part, long whole) { return whole == 0 ? 0 : (int) Math.round(part * 100.0 / whole); }
}
