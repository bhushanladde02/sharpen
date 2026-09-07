package io.sharpen.config;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Enums.SessionSource;
import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.Person;
import io.sharpen.domain.UsageSession;
import io.sharpen.repo.PersonRepository;
import io.sharpen.repo.UsageSessionRepository;
import io.sharpen.service.PersonService;
import io.sharpen.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Random;

/**
 * Seeds example accounts so the first run shows a working product. Enabled by {@code sharpen.demo-data=true}
 * (the default in development, off in the postgres profile). Skips silently if the demo user already exists.
 *
 * Sign in as demo@sharpen.io / demo1234 (individual) or hiring@sharpen.io / demo1234 (company).
 */
@Configuration
@ConditionalOnProperty(name = "sharpen.demo-data", havingValue = "true")
public class DemoDataLoader {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    @Bean
    ApplicationRunner seedDemoData(PersonRepository people, UsageSessionRepository sessions,
                                   PersonService personService, ReportService reports) {
        return args -> {
            if (people.findByEmailIgnoreCase("demo@sharpen.io").isPresent()) return;
            LocalDate today = LocalDate.now();

            Person demo = personService.register("demo@sharpen.io", "demo1234", "Priya Natarajan", AccountType.INDIVIDUAL);
            demo.setHeadline("Backend engineer who uses AI for review, not for first drafts");
            demo.setJobTitle("Senior Software Engineer");
            demo.setIndustry("Software");
            demo.setYearsExperience(9);
            demo.setLocation("Sacramento, CA");
            demo.setPrimaryTools("Claude, GitHub Copilot, ChatGPT");
            people.save(demo);
            seed(sessions, demo, new Random(7), today, 120, 62, 0.78, 0.45, 4.0);

            Person dep = personService.register("marcus@sharpen.io", "demo1234", "Marcus Ellery", AccountType.INDIVIDUAL);
            dep.setHeadline("Marketing analyst, heavy ChatGPT user");
            dep.setJobTitle("Marketing Analyst");
            dep.setIndustry("Consumer goods");
            dep.setYearsExperience(4);
            dep.setLocation("Austin, TX");
            dep.setPrimaryTools("ChatGPT, Gemini");
            people.save(dep);
            seed(sessions, dep, new Random(11), today, 120, 24, 0.35, 0.15, 3.6);

            Person strong = personService.register("lena@sharpen.io", "demo1234", "Lena Okafor", AccountType.INDIVIDUAL);
            strong.setHeadline("Data scientist; AI as a sparring partner");
            strong.setJobTitle("Data Scientist");
            strong.setIndustry("Healthcare");
            strong.setYearsExperience(7);
            strong.setLocation("Remote (Chicago)");
            strong.setPrimaryTools("Claude, Cursor, Perplexity");
            people.save(strong);
            seed(sessions, strong, new Random(23), today, 120, 78, 0.9, 0.6, 4.3);

            Person hiring = personService.register("hiring@sharpen.io", "demo1234", "Northwind Talent", AccountType.COMPANY);
            hiring.setHeadline("Hiring for engineering and analytics roles");
            hiring.setPublicProfile(false);
            people.save(hiring);

            for (Person p : List.of(demo, dep, strong)) {
                YearMonth m = YearMonth.from(today).minusMonths(1);
                for (int i = 0; i < 3; i++) reports.generate(p, m.minusMonths(i));
            }
            log.info("Seeded demo accounts: demo@sharpen.io, marcus@sharpen.io, lena@sharpen.io, hiring@sharpen.io (password demo1234)");
        };
    }

    /**
     * @param humanMean   average human contribution percentage
     * @param verifyRate  probability a session is verified
     * @param learnRate   probability the person learned something
     * @param outcomeMean average outcome on the 1-5 scale
     */
    private static void seed(UsageSessionRepository sessions, Person p, Random rnd, LocalDate today, int days,
                             int humanMean, double verifyRate, double learnRate, double outcomeMean) {
        String[] tools = p.getPrimaryTools().split(",\\s*");
        TaskCategory[] cats = {TaskCategory.CODING, TaskCategory.WRITING, TaskCategory.RESEARCH, TaskCategory.ANALYSIS,
                TaskCategory.LEARNING, TaskCategory.PLANNING, TaskCategory.ADMIN};
        for (int d = days; d >= 1; d--) {
            LocalDate date = today.minusDays(d);
            boolean weekend = date.getDayOfWeek().getValue() >= 6;
            int n = weekend ? (rnd.nextInt(3) == 0 ? 1 : 0) : rnd.nextInt(3);
            for (int i = 0; i < n; i++) {
                UsageContext ctx = weekend || rnd.nextInt(4) == 0 ? UsageContext.PERSONAL : UsageContext.PROFESSIONAL;
                TaskCategory cat = ctx == UsageContext.PERSONAL ? cats[rnd.nextInt(cats.length)]
                        : (rnd.nextInt(3) == 0 ? cats[rnd.nextInt(cats.length)] : cats[0]);
                UsageSession s = new UsageSession(p, date, ctx, tools[rnd.nextInt(tools.length)], cat);
                s.setDurationMinutes(10 + rnd.nextInt(80));
                s.setPromptCount(2 + rnd.nextInt(12));
                s.setHumanContributionPct(clamp((int) Math.round(humanMean + rnd.nextGaussian() * 18), 0, 100));
                s.setVerifiedOutput(rnd.nextDouble() < verifyRate);
                s.setLearnedSomething(rnd.nextDouble() < learnRate);
                s.setOutcome(clamp((int) Math.round(outcomeMean + rnd.nextGaussian() * 0.8), 1, 5));
                boolean imported = rnd.nextInt(6) == 0;
                s.setSource(imported ? SessionSource.EXTENSION : SessionSource.MANUAL);
                s.setSelfAssessed(!imported || d > 14);
                if (imported) s.setExternalId("ext-" + p.getHandle() + "-" + date + "-" + i);
                sessions.save(s);
            }
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
