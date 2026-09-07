package io.sharpen.service;

import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.UsageSession;
import io.sharpen.scoring.AiScore;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Everything the dashboard and the monthly report need about one month, computed once from the session list. */
public record MonthSummary(
        String yearMonth,
        int sessions,
        int assessedSessions,
        int minutes,
        int personalMinutes,
        int professionalMinutes,
        int prompts,
        long tokens,
        int activeDays,
        AiScore score,
        List<Share> tools,
        List<Share> categories,
        List<WeekBar> weeks,
        Map<String, Integer> sources
) {

    public record Share(String name, int minutes, int sessions, int pct) {}

    /** Minutes per calendar week of the month, split by context, for the small bar chart. */
    public record WeekBar(String label, int personalMinutes, int professionalMinutes) {}

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    public String label() { return YearMonth.parse(yearMonth).format(LABEL); }

    public double hours() { return Math.round(minutes / 6.0) / 10.0; }

    public int personalPct() { return minutes == 0 ? 0 : (int) Math.round(100.0 * personalMinutes / minutes); }

    public int professionalPct() { return minutes == 0 ? 0 : 100 - personalPct(); }

    /** Tallest week bar, for scaling the weekly chart; at least 1 so empty months divide safely. */
    public int maxWeekMinutes() {
        return Math.max(1, weeks.stream().mapToInt(w -> w.personalMinutes() + w.professionalMinutes()).max().orElse(1));
    }

    public static MonthSummary of(YearMonth month, List<UsageSession> list, AiScore score) {
        int minutes = 0, personal = 0, prompts = 0, assessed = 0;
        long tokens = 0;
        Set<Integer> days = new HashSet<>();
        Map<String, int[]> tools = new LinkedHashMap<>();
        Map<TaskCategory, int[]> cats = new EnumMap<>(TaskCategory.class);
        Map<String, Integer> sources = new TreeMap<>();
        int[][] weekMinutes = new int[6][2];

        for (UsageSession s : list) {
            minutes += s.getDurationMinutes();
            prompts += s.getPromptCount();
            if (s.getContext() == UsageContext.PERSONAL) personal += s.getDurationMinutes();
            if (s.isSelfAssessed()) assessed++;
            if (s.getTokensIn() != null) tokens += s.getTokensIn();
            if (s.getTokensOut() != null) tokens += s.getTokensOut();
            days.add(s.getOccurredOn().getDayOfMonth());
            tools.computeIfAbsent(s.getTool(), k -> new int[2]);
            tools.get(s.getTool())[0] += s.getDurationMinutes();
            tools.get(s.getTool())[1]++;
            cats.computeIfAbsent(s.getTaskCategory(), k -> new int[2]);
            cats.get(s.getTaskCategory())[0] += s.getDurationMinutes();
            cats.get(s.getTaskCategory())[1]++;
            sources.merge(s.getSource().label, 1, Integer::sum);
            int week = (s.getOccurredOn().getDayOfMonth() - 1) / 7;
            weekMinutes[week][s.getContext() == UsageContext.PERSONAL ? 0 : 1] += s.getDurationMinutes();
        }

        final int total = minutes;
        List<Share> toolShares = tools.entrySet().stream()
                .map(e -> new Share(e.getKey(), e.getValue()[0], e.getValue()[1], pct(e.getValue()[0], total)))
                .sorted(Comparator.comparingInt(Share::minutes).reversed())
                .limit(6).toList();
        List<Share> catShares = cats.entrySet().stream()
                .map(e -> new Share(e.getKey().label, e.getValue()[0], e.getValue()[1], pct(e.getValue()[0], total)))
                .sorted(Comparator.comparingInt(Share::minutes).reversed())
                .toList();

        int weeksInMonth = (month.lengthOfMonth() + 6) / 7;
        List<WeekBar> weeks = new ArrayList<>();
        for (int w = 0; w < weeksInMonth; w++) {
            int from = w * 7 + 1, to = Math.min(month.lengthOfMonth(), w * 7 + 7);
            weeks.add(new WeekBar(from + "–" + to, weekMinutes[w][0], weekMinutes[w][1]));
        }

        return new MonthSummary(month.toString(), list.size(), assessed, minutes, personal, minutes - personal,
                prompts, tokens, days.size(), score, toolShares, catShares, weeks, sources);
    }

    public static MonthSummary empty(YearMonth month) {
        return new MonthSummary(month.toString(), 0, 0, 0, 0, 0, 0, 0, 0, AiScore.empty(),
                List.of(), List.of(), List.of(), Map.of());
    }

    private static int pct(int part, int total) { return total == 0 ? 0 : (int) Math.round(100.0 * part / total); }
}
