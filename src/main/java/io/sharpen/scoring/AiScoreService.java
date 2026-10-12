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

        double independence = weightedMean(assessed, totalWeight, AiScoreService::ownership);
        double outcome = weightedMean(assessed, totalWeight, s -> (s.getOutcome() - 1) * 25.0); // 1..5 -> 0..100
        double promptEfficiency = weightedMean(assessed, totalWeight, AiScoreService::promptEfficiency);
        double effectiveness = 0.7 * outcome + 0.3 * promptEfficiency;
        double verification = weightedMean(assessed, totalWeight, s -> s.isVerifiedOutput() ? 100.0 : 0.0);
        double growth = weightedMean(assessed, totalWeight, s -> s.isLearnedSomething() ? 100.0 : 0.0);
        double breadth = breadth(assessed);

        double composite = W_INDEPENDENCE * independence + W_EFFECTIVENESS * effectiveness
                + W_VERIFICATION * verification + W_GROWTH * growth + W_BREADTH * breadth;

        boolean relianceRisk = relianceRisk(assessed);
        Confidence confidence = assessed.size() >= MIN_SESSIONS_FOR_FULL_CONFIDENCE ? Confidence.FULL : Confidence.LOW;

        List<Flag> flags = flags(assessed, independence, verification, growth, breadth, relianceRisk, confidence,
                sessions.size() - assessed.size());

        return new AiScore(
                (int) Math.round(composite),   // 0-100, like each dimension (the weights add up to 1)
                round(independence), round(effectiveness), round(verification), round(growth), round(breadth),
                sessions.size(), assessed.size(), confidence, relianceRisk, flags);
    }

    /** The share of a checked session's model-written work that counts as the person's own. */
    static final double CHECKED_MODEL_WORK = 0.7;

    /**
     * How much of the finished work the person owns, 0–100: what they did themselves, plus — when they checked the
     * output — 70 % of what the model did. Someone who has the model write 80 % and then tests it owns 76, not 20;
     * someone who writes 90 % and checks nothing owns 90 but scores 0 on verification. Judgement, not typing, is what
     * the dimension rewards (since 12 October 2026; before, it was the human share alone).
     */
    public static double ownership(UsageSession s) {
        double human = Math.max(0, Math.min(100, s.getHumanContributionPct()));
        return s.isVerifiedOutput() ? human + (100 - human) * CHECKED_MODEL_WORK : human;
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

    /**
     * More than half of professional minutes were spent in sessions where the model did most of the work <b>and</b>
     * nobody checked it. Letting the model write most of something is not the risk — handing it on unchecked is:
     * someone who generates boilerplate and then tests it is using AI well, and must not be flagged for it.
     */
    static boolean relianceRisk(List<UsageSession> assessed) {
        double proMinutes = 0, uncheckedModelMinutes = 0;
        for (UsageSession s : assessed) {
            if (s.getContext() != UsageContext.PROFESSIONAL) continue;
            double w = weight(s);
            proMinutes += w;
            if (s.getHumanContributionPct() < 30 && !s.isVerifiedOutput()) uncheckedModelMinutes += w;
        }
        return proMinutes > 0 && uncheckedModelMinutes / proMinutes > 0.5;
    }

    /**
     * Every rated session got the best possible answers: checked, learned something, outcome 5 of 5. Real work
     * varies, so this usually means the ratings are generous rather than the month was flawless. It does not change
     * the number — it is shown next to it, on the profile too, so a reader knows how to weigh it.
     */
    static boolean uniformRatings(List<UsageSession> assessed) {
        if (assessed.size() < MIN_SESSIONS_FOR_FULL_CONFIDENCE) return false;
        return assessed.stream().allMatch(s -> s.isVerifiedOutput() && s.isLearnedSomething() && s.getOutcome() >= 5);
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
        if (assessed.size() < 3) return flags;   // habit flags need a few sessions before they mean anything
        if (relianceRisk) {
            flags.add(new Flag("risk", "Over half of your professional AI time was work the model did and nobody checked."));
        }
        if (uniformRatings(assessed)) {
            flags.add(new Flag("uniform", "Every rated session got the top answers. Real work varies, so read this score as a generous self-assessment."));
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
