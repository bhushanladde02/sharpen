package io.sharpen.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The 60-second habits check: scoring, types and the shareable code. No Spring context. */
class HabitCheckTest {

    /** The answer index worth the most points not above {@code points} on question i (options are not best-first). */
    private static char pick(int i, int points) {
        var options = HabitCheck.QUESTIONS.get(i).options();
        int best = -1;
        for (int k = 0; k < options.size(); k++) {
            int p = options.get(k).points();
            if (p <= points && (best < 0 || p > options.get(best).points())) best = k;
        }
        return (char) ('0' + best);
    }

    private static String all(int points) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < HabitCheck.QUESTIONS.size(); i++) sb.append(pick(i, points));
        return sb.toString();
    }

    @Test
    void bestAndWorstAnswersSpanTheScale() {
        HabitCheck.Result best = HabitCheck.score(all(3)), worst = HabitCheck.score(all(0));
        assertThat(best.composite()).isEqualTo(100);
        assertThat(best.type()).isEqualTo("The Pilot");
        assertThat(best.band()).isEqualTo("Expert");
        assertThat(worst.composite()).isZero();
        assertThat(worst.type()).isEqualTo("The Passenger");
        assertThat(best.dimensions()).extracting(HabitCheck.DimensionScore::label)
                .containsExactly("Independence", "Effectiveness", "Verification", "Growth", "Breadth");
    }

    @Test
    void theTypeFollowsTheStrongestHumanHabit() {
        // middling everywhere, verification best → The Editor, and the tip goes after the weakest habit
        StringBuilder editor = new StringBuilder(all(1));
        editor.setCharAt(2, pick(2, 3)); editor.setCharAt(3, pick(3, 3));
        HabitCheck.Result r = HabitCheck.score(editor.toString());
        assertThat(r.type()).isEqualTo("The Editor");
        assertThat(r.tip()).doesNotContain("run one test");

        StringBuilder soloist = new StringBuilder(all(1));
        soloist.setCharAt(0, pick(0, 3)); soloist.setCharAt(1, pick(1, 3));
        HabitCheck.Result s = HabitCheck.score(soloist.toString());
        assertThat(s.type()).isEqualTo("The Soloist");
        assertThat(s.tip()).contains("explain");   // growth is the weakest habit here
    }

    @Test
    void weightsMatchTheAiScore() {
        double sum = 0;
        for (HabitCheck.Dimension d : HabitCheck.Dimension.values()) sum += d.weight;
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(HabitCheck.QUESTIONS).hasSize(9).allSatisfy(q -> assertThat(q.options()).hasSize(4));
    }

    @Test
    void onlyNineDigitsFromZeroToThreeAreACode() {
        assertThat(HabitCheck.score("012301230")).isNotNull();
        for (String bad : new String[]{null, "", "01230123", "0123012301", "012301234", "01230123a", "../../etc"}) {
            assertThat(HabitCheck.score(bad)).as(bad).isNull();
        }
    }
}
