package io.sharpen.web;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Person;
import io.sharpen.repo.PersonRepository;
import io.sharpen.scoring.AiScore;
import io.sharpen.service.SessionService;
import io.sharpen.service.StatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The company view: every public individual profile with its rolling score, filterable by text and sortable.
 * Volume (hours) is shown but never used for ranking — the point is how someone works with AI, not how much.
 */
@Controller
public class CandidateController {

    public record Candidate(Person person, AiScore score, long sessions, List<String> tools) {}

    private final PersonRepository people;
    private final StatsService stats;
    private final SessionService sessions;

    public CandidateController(PersonRepository people, StatsService stats, SessionService sessions) {
        this.people = people;
        this.stats = stats;
        this.sessions = sessions;
    }

    @GetMapping("/candidates")
    public String candidates(@RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "score") String sort,
                             Model model) {
        LocalDate today = LocalDate.now();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Candidate> list = people.findByAccountTypeAndPublicProfileTrue(AccountType.INDIVIDUAL).stream()
                .map(p -> new Candidate(p, stats.rollingScore(p, today), sessions.count(p),
                        stats.tools(p).stream().map(StatsService.ToolUse::name).toList()))
                .filter(c -> needle.isEmpty() || haystack(c).contains(needle))
                .sorted(comparator(sort))
                .toList();
        model.addAttribute("candidates", list);
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("sort", sort);
        return "candidates";
    }

    private static Comparator<Candidate> comparator(String sort) {
        Comparator<Candidate> byScore = Comparator.comparingInt((Candidate c) -> c.score().composite()).reversed();
        return switch (sort) {
            case "independence" -> Comparator.comparingInt((Candidate c) -> c.score().independence()).reversed().thenComparing(byScore);
            case "verification" -> Comparator.comparingInt((Candidate c) -> c.score().verification()).reversed().thenComparing(byScore);
            case "growth" -> Comparator.comparingInt((Candidate c) -> c.score().growth()).reversed().thenComparing(byScore);
            case "name" -> Comparator.comparing((Candidate c) -> c.person().getDisplayName(), String.CASE_INSENSITIVE_ORDER);
            default -> byScore;
        };
    }

    private static String haystack(Candidate c) {
        Person p = c.person();
        return String.join(" ", nz(p.getDisplayName()), nz(p.getHeadline()), nz(p.getJobTitle()), nz(p.getIndustry()),
                nz(p.getLocation()), String.join(" ", c.tools())).toLowerCase(Locale.ROOT);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
