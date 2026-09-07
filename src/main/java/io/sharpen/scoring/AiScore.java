package io.sharpen.scoring;

import java.util.List;

/**
 * The AI score for a set of sessions. Composite is 0-1000; each dimension is 0-100.
 *
 * <ul>
 *   <li><b>independence</b> — how much of the work the person did themselves (human contribution)</li>
 *   <li><b>effectiveness</b> — outcome quality per prompt spent</li>
 *   <li><b>verification</b> — share of sessions where the output was checked</li>
 *   <li><b>growth</b> — share of sessions where the person learned something they can now do alone</li>
 *   <li><b>breadth</b> — spread across task types and tools, so the score reflects a rounded practitioner</li>
 * </ul>
 *
 * @param confidence   LOW below {@link AiScoreService#MIN_SESSIONS_FOR_FULL_CONFIDENCE} assessed sessions
 * @param relianceRisk true when most professional sessions were mostly done by the model
 */
public record AiScore(
        int composite,
        int independence,
        int effectiveness,
        int verification,
        int growth,
        int breadth,
        int sessionCount,
        int assessedCount,
        Confidence confidence,
        boolean relianceRisk,
        List<Flag> flags
) {

    public enum Confidence { NONE, LOW, FULL }

    /** A short, human-readable observation shown next to the score. */
    public record Flag(String kind, String message) {}

    public static AiScore empty() {
        return new AiScore(0, 0, 0, 0, 0, 0, 0, 0, Confidence.NONE, false, List.of());
    }

    public String band() {
        if (confidence == Confidence.NONE) return "Not yet scored";
        if (composite >= 800) return "Expert";
        if (composite >= 650) return "Strong";
        if (composite >= 500) return "Developing";
        if (composite >= 350) return "Dependent";
        return "At risk";
    }

    public boolean hasScore() { return confidence != Confidence.NONE; }

    /** stroke-dashoffset for the r=52 score ring (circumference ≈ 326.7): full circle at 1000. */
    public String ringOffset() {
        double c = 2 * Math.PI * 52;
        return String.format(java.util.Locale.ROOT, "%.1f", c * (1 - Math.min(1000, Math.max(0, composite)) / 1000.0));
    }
}
