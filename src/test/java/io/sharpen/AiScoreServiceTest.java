package io.sharpen;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.Person;
import io.sharpen.domain.UsageSession;
import io.sharpen.scoring.AiScore;
import io.sharpen.scoring.AiScoreService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiScoreServiceTest {

    private final AiScoreService service = new AiScoreService();
    private final Person person = new Person("t@example.com", "x", "Test", "test", AccountType.INDIVIDUAL, "k");

    private UsageSession session(int minutes, int human, boolean verified, boolean learned, int outcome,
                                 TaskCategory cat, String tool, UsageContext ctx, boolean assessed) {
        UsageSession s = new UsageSession(person, LocalDate.of(2026, 8, 10), ctx, tool, cat);
        s.setDurationMinutes(minutes);
        s.setPromptCount(4);
        s.setHumanContributionPct(human);
        s.setVerifiedOutput(verified);
        s.setLearnedSomething(learned);
        s.setOutcome(outcome);
        s.setSelfAssessed(assessed);
        return s;
    }

    @Test
    void noSessionsGivesNoScore() {
        AiScore s = service.compute(List.of());
        assertThat(s.hasScore()).isFalse();
        assertThat(s.band()).isEqualTo("Not yet scored");
    }

    @Test
    void unratedSessionsDoNotCount() {
        AiScore s = service.compute(List.of(
                session(60, 0, false, false, 5, TaskCategory.CODING, "Claude", UsageContext.PROFESSIONAL, false)));
        assertThat(s.hasScore()).isFalse();
        assertThat(s.sessionCount()).isEqualTo(1);
        assertThat(s.flags()).anyMatch(f -> f.kind().equals("assess"));
    }

    @Test
    void strongPractitionerScoresHigh() {
        List<UsageSession> list = new ArrayList<>();
        TaskCategory[] cats = TaskCategory.values();
        String[] tools = {"Claude", "ChatGPT", "Cursor"};
        for (int i = 0; i < 10; i++) {
            list.add(session(45, 85, true, i % 2 == 0, 5, cats[i % 6], tools[i % 3], UsageContext.PROFESSIONAL, true));
        }
        AiScore s = service.compute(list);
        assertThat(s.confidence()).isEqualTo(AiScore.Confidence.FULL);
        assertThat(s.independence()).isEqualTo(85);
        assertThat(s.verification()).isEqualTo(100);
        assertThat(s.breadth()).isEqualTo(100);
        assertThat(s.composite()).isGreaterThan(800);
        assertThat(s.relianceRisk()).isFalse();
        assertThat(s.band()).isEqualTo("Expert");
    }

    @Test
    void relianceRiskWhenModelDoesTheWork() {
        List<UsageSession> list = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            list.add(session(30, 15, false, false, 4, TaskCategory.WRITING, "ChatGPT", UsageContext.PROFESSIONAL, true));
        }
        AiScore s = service.compute(list);
        assertThat(s.relianceRisk()).isTrue();
        assertThat(s.composite()).isLessThan(400);
        assertThat(s.flags()).anyMatch(f -> f.kind().equals("risk"));
    }

    @Test
    void hoursDoNotRaiseTheScore() {
        List<UsageSession> few = List.of(
                session(30, 60, true, true, 4, TaskCategory.CODING, "Claude", UsageContext.PROFESSIONAL, true),
                session(30, 60, true, true, 4, TaskCategory.WRITING, "Claude", UsageContext.PERSONAL, true));
        List<UsageSession> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) many.addAll(few);
        assertThat(service.compute(many).independence()).isEqualTo(service.compute(few).independence());
        assertThat(service.compute(many).verification()).isEqualTo(service.compute(few).verification());
        assertThat(service.compute(many).breadth()).isEqualTo(service.compute(few).breadth());
    }

    @Test
    void longSessionsWeighMore() {
        List<UsageSession> list = List.of(
                session(240, 100, true, true, 5, TaskCategory.CODING, "Claude", UsageContext.PROFESSIONAL, true),
                session(5, 0, false, false, 1, TaskCategory.CODING, "Claude", UsageContext.PROFESSIONAL, true));
        assertThat(service.compute(list).independence()).isGreaterThan(90);
    }
}
