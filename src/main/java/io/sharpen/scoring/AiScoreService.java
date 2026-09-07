package io.sharpen.scoring;

import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.UsageSession;
import io.sharpen.scoring.AiScore.Confidence;
import io.sharpen.scoring.AiScore.Flag;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pure scoring — no persistence, so it is trivially unit-testable and can move to a batch job unchanged.
 *
 * Design rules:
 * <ol>
 *   <li>Only self-assessed sessions count. Imported usage shows volume but cannot inflate or sink the score
 *       until the person rates it.</li>
 *   <li>More hours never raise the score. Every dimension is a rate or a mean, not a total.</li>
 *   <li>Sessions are minute-weighted so a two-hour build counts more than a two-minute lookup.</li>
 * </ol>
 */
@Service
public class AiScoreService {

    public static final int MIN_SESSIONS_FOR_FULL_CONFIDENCE = 8;

    static final double W_INDEPENDENCE = 0.30;
    static final double W_EFFECTIVENESS = 0.20;
    static final double W_VERIFICATION = 0.20;
    static final double W_GROWTH = 0.20;
    static final double W_BREADTH = 0.10;

    public AiScore compute(List<UsageSession> sessions) {
        List<UsageSession> assessed = sessions.stream().filter(UsageSession::isSelfAssessed).toList();
        if (assessed.isEmpty()) {
            return sessions.isEmpty() ? AiScore.empty()
                    : new AiScore(0, 0, 0, 0, 0, 0, sessions.size(), 0, Confidence.NONE, false,
                    List.of(new Flag("assess", "Rate your imported sessions to get a score.")));
        }

        double totalWeight = assessed.stream().mapToDouble(AiScoreService::weight).sum();

        double independence = weightedMean(assessed, totalWeight, s -> s.getHumanContributionPct());
        double outcome = weightedMean(assessed, totalWeight, s -> (s.getOutcome() - 1) * 25.0); // 1..5 -> 0..100
        double promptEfficiency = weightedMean(assessed, totalWeight, AiScoreService::promptEfficiency);
        double effectiveness = 0.7 * outcome + 0.3 * promptEfficiency;
        double verification = weightedMean(assessed, totalWeight, s -> s.isVerifiedOutput() ? 100.0 : 0.0);
        double growth = weightedMean(assessed, totalWeight, s -> s.isLearnedSomething() ? 100.0 : 0.0);
        double breadth = breadth(assessed);

        double composite10 = W_INDEPENDENCE * independence + W_EFFECTIVENESS * effectiveness
                + W_VERIFICATION * verification + W_GROWTH * growth + W_BREADTH * breadth;

        boolean relianceRisk = relianceRisk(assessed);
        Confidence confidence = assessed.size() >= MIN_SESSIONS_FOR_FULL_CONFIDENCE ? Confidence.FULL : Confidence.LOW;

        List<Flag> flags = flags(assessed, independence, verification, growth, breadth, relianceRisk, confidence,
                sessions.size() - assessed.size());

        return new AiScore(
                (int) Math.round(composite10 * 10),
                round(independence), round(effectiveness), round(verification), round(growth), round(breadth),
                sessions.size(), assessed.size(), confidence, relianceRisk, flags);
    }

    /** Minutes, floored at 5 so a quick question still counts, capped at 4h so one marathon cannot dominate a month. */
    static double weight(UsageSession s) {
        return Math.max(5, Math.min(240, s.getDurationMinutes()));
    }

    /** 100 when a good result took few prompts; falls as prompts per useful outcome rise. */
    static double promptEfficiency(UsageSession s) {
        if (s.getPromptCount() <= 0) return 60; // unknown prompt count: neutral
        double promptsPerOutcomePoint = (double) s.getPromptCount() / Math.max(1, s.getOutcome());
        // 1 prompt per outcome point -> 100, 5 -> ~50, 15+ -> ~15
        return Math.max(0, Math.min(100, 100.0 / (1 + (promptsPerOutcomePoint - 1) / 4.0)));
    }

    /** Distinct task categories out of 6 count for two thirds, distinct tools out of 3 for one third. */
    static double breadth(List<UsageSession> assessed) {
        Set<TaskCategory> categories = assessed.stream().map(UsageSession::getTaskCategory).collect(Collectors.toSet());
        Set<String> tools = assessed.stream().map(s -> s.getTool().trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        double catScore = Math.min(1.0, categories.size() / 6.0) * 100;
        double toolScore = Math.min(1.0, tools.size() / 3.0) * 100;
        return (2 * catScore + toolScore) / 3;
    }

    /** More than half of professional minutes were spent in sessions where the model did most of the work. */
    static boolean relianceRisk(List<UsageSession> assessed) {
        double proMinutes = 0, lowHumanMinutes = 0;
        for (UsageSession s : assessed) {
            if (s.getContext() != UsageContext.PROFESSIONAL) continue;
            double w = weight(s);
            proMinutes += w;
            if (s.getHumanContributionPct() < 30) lowHumanMinutes += w;
        }
        return proMinutes > 0 && lowHumanMinutes / proMinutes > 0.5;
    }

    private static List<Flag> flags(List<UsageSession> assessed, double independence, double verification,
                                    double growth, double breadth, boolean relianceRisk, Confidence confidence,
                                    int unassessed) {
        List<Flag> flags = new ArrayList<>();
        if (confidence == Confidence.LOW) {
            flags.add(new Flag("confidence", "Provisional — based on " + assessed.size() + " rated session"
                    + (assessed.size() == 1 ? "" : "s") + "; " + MIN_SESSIONS_FOR_FULL_CONFIDENCE + " gives full confidence."));
        }
        if (unassessed > 0) {
            flags.add(new Flag("assess", unassessed + " imported session" + (unassessed == 1 ? "" : "s")
                    + " not rated yet — they do not count until you rate them."));
        }
        if (relianceRisk) {
            flags.add(new Flag("risk", "Over half of your professional AI time was work the model did for you."));
        }
        if (verification < 50) {
            flags.add(new Flag("verify", "Fewer than half of your sessions were checked. Verify one claim or run one test per session."));
        }
        if (growth < 30) {
            flags.add(new Flag("growth", "You rarely leave a session able to do something new. Ask the model to explain, then redo one step yourself."));
        }
        if (independence >= 70 && verification >= 70) {
            flags.add(new Flag("strength", "You lead the work and check the output — the pattern employers pay for."));
        }
        if (breadth < 40 && assessed.size() >= 5) {
            flags.add(new Flag("breadth", "Usage is concentrated in one or two task types. That is fine for depth; note it on your profile."));
        }
        return flags;
    }

    private static double weightedMean(List<UsageSession> sessions, double totalWeight, java.util.function.ToDoubleFunction<UsageSession> f) {
        double sum = 0;
        for (UsageSession s : sessions) sum += weight(s) * f.applyAsDouble(s);
        return totalWeight == 0 ? 0 : sum / totalWeight;
    }

    private static int round(double v) { return (int) Math.round(Math.max(0, Math.min(100, v))); }
}
