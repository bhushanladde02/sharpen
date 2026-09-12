package io.sharpen.service;

import io.sharpen.domain.Person;
import io.sharpen.domain.UsageSession;
import io.sharpen.scoring.AiScore;
import io.sharpen.scoring.AiScoreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-side aggregates: one query per request, grouped in memory. Sessions per person per month are small. */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private final SessionService sessions;
    private final AiScoreService scoring;

    public StatsService(SessionService sessions, AiScoreService scoring) {
        this.sessions = sessions;
        this.scoring = scoring;
    }

    public MonthSummary month(Person person, YearMonth month) {
        List<UsageSession> list = sessions.inMonth(person, month);
        return list.isEmpty() ? MonthSummary.empty(month) : MonthSummary.of(month, list, scoring.compute(list));
    }

    /** One tool on a profile: name, total minutes, number of sessions (0/0 when it was only listed by hand). */
    public record ToolUse(String name, long minutes, long sessions) {}

    /**
     * The tools shown on a person's profile: everything they have logged sessions with, most minutes first,
     * followed by anything they typed into Settings that has not appeared in a session yet. Matching is
     * case-insensitive so "claude code" and "Claude Code" are one chip.
     */
    public List<ToolUse> tools(Person person) {
        List<ToolUse> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Object[] row : sessions.toolTotals(person)) {
            String name = ((String) row[0]).trim();
            if (name.isEmpty() || !seen.add(name.toLowerCase(java.util.Locale.ROOT))) continue;
            out.add(new ToolUse(name, ((Number) row[1]).longValue(), ((Number) row[2]).longValue()));
        }
        if (person.getPrimaryTools() != null) {
            for (String t : person.getPrimaryTools().split("\\s*,\\s*")) {
                String name = t.trim();
                if (!name.isEmpty() && seen.add(name.toLowerCase(java.util.Locale.ROOT))) out.add(new ToolUse(name, 0, 0));
            }
        }
        return out;
    }

    /** The trailing-90-day score shown on the dashboard and the public profile. */
    public AiScore rollingScore(Person person, LocalDate today) {
        return scoring.compute(sessions.between(person, today.minusDays(89), today));
    }

    /** Oldest first, one summary per month, covering the last {@code months} months ending at {@code end}. */
    public List<MonthSummary> trend(Person person, YearMonth end, int months) {
        YearMonth start = end.minusMonths(months - 1L);
        List<UsageSession> all = sessions.between(person, start.atDay(1), end.atEndOfMonth());
        Map<YearMonth, List<UsageSession>> byMonth = all.stream()
                .collect(Collectors.groupingBy(s -> YearMonth.from(s.getOccurredOn())));
        List<MonthSummary> out = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            List<UsageSession> list = byMonth.getOrDefault(m, List.of());
            out.add(list.isEmpty() ? MonthSummary.empty(m) : MonthSummary.of(m, list, scoring.compute(list)));
        }
        return out;
    }
}
