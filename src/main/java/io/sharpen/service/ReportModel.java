package io.sharpen.service;

import java.util.List;

/**
 * The monthly report, exactly as rendered on screen and in the PDF. Stored as JSON on {@code MonthlyReport}.
 * {@code scale} is the top of the AI score scale the numbers were written on: 100 since 12 October 2026. Reports
 * stored before then have none (they were on 0–1000) and are converted by {@code ReportService}.
 */
public record ReportModel(
        String personName,
        String handle,
        String headline,
        String yearMonth,
        String label,
        String generatedAt,
        MonthSummary month,
        MonthSummary previous,
        Delta delta,
        BrainActive brainActive,
        List<Insight> insights,
        List<String> nextMonth,
        Integer scale
) {

    /** The current AI score scale (0–100). */
    public static final int SCALE = 100;

    /** Month-over-month change; null fields mean there was no previous month to compare with. */
    public record Delta(Integer score, Integer independence, Integer effectiveness, Integer verification,
                        Integer growth, Integer breadth, Integer minutes, Integer sessions) {}

    /** The "keep the brain sharp" panel: what share of the work stayed human. */
    public record BrainActive(int humanContributionPct, int verifiedPct, int learnedPct,
                              int humanMinutes, int modelMinutes, String verdict) {}

    public record Insight(String kind, String title, String body) {}

    public boolean hasPrevious() { return previous != null && previous.sessions() > 0; }
}
