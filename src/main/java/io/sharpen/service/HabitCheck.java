package io.sharpen.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The 60-second AI habits check at {@code /check}: nine multiple-choice questions, no account, nothing stored. The
 * answers are carried in the result's address ({@code /check/r/<nine digits>}), so a result can be shared and
 * reopened without Sharpen keeping anything. It scores the same five things as the AI score, with the same weights
 * and bands, so it is an honest preview of what logging real sessions would show — and, like the score, it is a
 * self-assessment, which the result page says.
 */
public final class HabitCheck {

    /** Same names and weights as {@link io.sharpen.scoring.AiScoreService}. */
    public enum Dimension {
        INDEPENDENCE("Independence", 0.30), EFFECTIVENESS("Effectiveness", 0.20), VERIFICATION("Verification", 0.20),
        GROWTH("Growth", 0.20), BREADTH("Breadth", 0.10);

        public final String label;
        public final double weight;

        Dimension(String label, double weight) { this.label = label; this.weight = weight; }
    }

    /** One answer as shown, and what it is worth (0–3). Options are listed in a natural order, not best-first. */
    public record Option(String text, int points) {}

    public record Question(int number, Dimension dimension, String text, List<Option> options) {}

    public record DimensionScore(String label, int value) {}

    /** A scored result. {@code code} is the nine answer indexes, the part of the address that makes it shareable. */
    /** One of the nine answers as given, so the result page can say what each number came from. */
    public record Answer(int number, String dimension, String question, String chosen, int points) {}

    public record Result(String code, int composite, String band, List<DimensionScore> dimensions,
                         String type, String typeLine, String tip, List<Answer> answers) {}

    private static final Pattern CODE = Pattern.compile("[0-3]{9}");

    public static final List<Question> QUESTIONS = List.of(
            new Question(1, Dimension.INDEPENDENCE, "When you start a task with AI, who does the first thinking?", List.of(
                    new Option("The AI writes it; I paste it in", 0),
                    new Option("The AI drafts; I edit what it gives me", 1),
                    new Option("I describe the problem and shape what comes back", 2),
                    new Option("I outline or try it first, then bring the AI in", 3))),
            new Question(2, Dimension.INDEPENDENCE, "Could you redo your last AI-assisted task without the AI?", List.of(
                    new Option("Yes, it would just take longer", 3),
                    new Option("Most of it", 2),
                    new Option("Only parts of it", 1),
                    new Option("No", 0))),
            new Question(3, Dimension.VERIFICATION, "Before you use what the AI produced, you…", List.of(
                    new Option("Use it if it looks right", 0),
                    new Option("Check it when something seems off", 1),
                    new Option("Usually check a fact or run it once", 2),
                    new Option("Test it or check it against a source, every time", 3))),
            new Question(4, Dimension.VERIFICATION, "The last time an AI was wrong in your work, who noticed?", List.of(
                    new Option("I did, before anyone else saw it", 3),
                    new Option("A colleague or a reviewer", 1),
                    new Option("A user or a customer", 0),
                    new Option("I can't remember it ever being wrong", 0))),
            new Question(5, Dimension.GROWTH, "How often do you finish a session able to do something new on your own?", List.of(
                    new Option("Most sessions", 3),
                    new Option("Now and then", 2),
                    new Option("Rarely", 1),
                    new Option("Never — that is not what I use it for", 0))),
            new Question(6, Dimension.GROWTH, "The AI gives you an answer you don't understand. You…", List.of(
                    new Option("Use it anyway if it works", 0),
                    new Option("Ask a colleague later", 2),
                    new Option("Ask it to explain, then redo a step myself", 3),
                    new Option("Look it up afterwards", 2))),
            new Question(7, Dimension.EFFECTIVENESS, "How often does a session give you something you actually use?", List.of(
                    new Option("Rarely", 0),
                    new Option("Sometimes", 1),
                    new Option("Often", 2),
                    new Option("Almost always", 3))),
            new Question(8, Dimension.EFFECTIVENESS, "To get a good result you usually need…", List.of(
                    new Option("One or two prompts", 3),
                    new Option("Three to five prompts", 2),
                    new Option("Many rounds of back and forth", 1),
                    new Option("I often give up", 0))),
            new Question(9, Dimension.BREADTH, "How many different kinds of work do you use AI for (code, writing, research, analysis, planning, learning…)?", List.of(
                    new Option("One", 0),
                    new Option("Two", 1),
                    new Option("Three or four", 2),
                    new Option("Five or more", 3))));

    private HabitCheck() {}

    public static boolean valid(String code) {
        return code != null && CODE.matcher(code).matches();
    }

    /** The result for nine answer indexes (each 0–3, the position of the chosen option), or null if malformed. */
    public static Result score(String code) {
        if (!valid(code)) return null;
        double[] sum = new double[Dimension.values().length];
        int[] count = new int[Dimension.values().length];
        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < QUESTIONS.size(); i++) {
            Question q = QUESTIONS.get(i);
            Option chosen = q.options().get(code.charAt(i) - '0');
            sum[q.dimension().ordinal()] += chosen.points();
            count[q.dimension().ordinal()]++;
            answers.add(new Answer(q.number(), q.dimension().label, q.text(), chosen.text(), chosen.points()));
        }
        List<DimensionScore> dims = new ArrayList<>();
        int[] value = new int[sum.length];
        double composite = 0;
        for (Dimension d : Dimension.values()) {
            double v = 100.0 * sum[d.ordinal()] / (3.0 * count[d.ordinal()]);
            value[d.ordinal()] = (int) Math.round(v);
            dims.add(new DimensionScore(d.label, value[d.ordinal()]));
            composite += d.weight * v;
        }
        int c = (int) Math.round(composite);
        String[] type = type(c, value);
        return new Result(code, c, band(c), dims, type[0], type[1], tip(value), answers);
    }

    /** The same bands as the AI score. */
    static String band(int c) {
        if (c >= 80) return "Expert";
        if (c >= 65) return "Strong";
        if (c >= 50) return "Developing";
        if (c >= 35) return "Building";
        return "Starting";
    }

    /** A name for the pattern: the overall level first, then whichever human habit is strongest. */
    static String[] type(int composite, int[] v) {
        if (composite >= 80) return new String[]{"The Pilot", "You fly it. The AI is the instrument panel: fast, useful, and checked."};
        if (composite < 35) return new String[]{"The Passenger", "The AI is doing the driving. One habit — checking its output — would change that fastest."};
        int own = v[Dimension.INDEPENDENCE.ordinal()], ver = v[Dimension.VERIFICATION.ordinal()], gro = v[Dimension.GROWTH.ordinal()];
        if (ver >= own && ver >= gro) return new String[]{"The Editor", "The AI drafts, you check. Your judgement is the part that cannot be automated."};
        if (own >= gro) return new String[]{"The Soloist", "You do the thinking yourself, and the AI speeds you up. Make checking its output a habit too."};
        return new String[]{"The Learner", "You use AI to get better, not just to get done. That compounds."};
    }

    /** One concrete thing to try, aimed at the weakest of the three habits Sharpen cares most about. */
    static String tip(int[] v) {
        int own = v[Dimension.INDEPENDENCE.ordinal()], ver = v[Dimension.VERIFICATION.ordinal()], gro = v[Dimension.GROWTH.ordinal()];
        if (ver <= own && ver <= gro) return "This week, run one test or check one claim in every AI session before you use the result.";
        if (own <= gro) return "Before your next prompt, write down your own first attempt — even three lines — then compare.";
        return "Once a day, ask the AI to explain its answer, then redo one step of it yourself.";
    }

    /** "I'm The Editor — 68/100 on the AI habits check" */
    public static String shareTitle(Result r) {
        return String.format(Locale.ROOT, "I'm %s — %d/100 on the AI habits check", r.type(), r.composite());
    }
}
