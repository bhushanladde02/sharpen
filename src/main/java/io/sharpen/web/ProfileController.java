package io.sharpen.web;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.MonthlyReport;
import io.sharpen.domain.Person;
import io.sharpen.repo.PersonRepository;
import io.sharpen.scoring.AiScore;
import io.sharpen.service.MonthSummary;
import io.sharpen.service.PersonService;
import io.sharpen.service.ReportService;
import io.sharpen.service.SessionService;
import io.sharpen.service.StatsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The public AI profile (the "LinkedIn of AI" page) and the settings page that edits it. */
@Controller
public class ProfileController {

    public static class ProfileForm {
        @NotBlank @Size(max = 120) private String displayName;
        @NotBlank @Size(max = 60) @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9-]+", message = "lower-case letters, digits and dashes only") private String handle;
        @Size(max = 160) private String headline;
        @Size(max = 120) private String jobTitle;
        @Size(max = 80) private String industry;
        @Min(0) @Max(60) private Integer yearsExperience;
        @Size(max = 120) private String location;
        @Size(max = 300) private String primaryTools;
        private boolean publicProfile;

        static ProfileForm from(Person p) {
            ProfileForm f = new ProfileForm();
            f.displayName = p.getDisplayName(); f.handle = p.getHandle(); f.headline = p.getHeadline();
            f.jobTitle = p.getJobTitle(); f.industry = p.getIndustry(); f.yearsExperience = p.getYearsExperience();
            f.location = p.getLocation(); f.primaryTools = p.getPrimaryTools(); f.publicProfile = p.isPublicProfile();
            return f;
        }

        void applyTo(Person p) {
            p.setDisplayName(displayName.trim()); p.setHandle(handle.trim()); p.setHeadline(blank(headline));
            p.setJobTitle(blank(jobTitle)); p.setIndustry(blank(industry)); p.setYearsExperience(yearsExperience);
            p.setLocation(blank(location)); p.setPrimaryTools(blank(primaryTools)); p.setPublicProfile(publicProfile);
        }

        private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String v) { displayName = v; }
        public String getHandle() { return handle; }
        public void setHandle(String v) { handle = v; }
        public String getHeadline() { return headline; }
        public void setHeadline(String v) { headline = v; }
        public String getJobTitle() { return jobTitle; }
        public void setJobTitle(String v) { jobTitle = v; }
        public String getIndustry() { return industry; }
        public void setIndustry(String v) { industry = v; }
        public Integer getYearsExperience() { return yearsExperience; }
        public void setYearsExperience(Integer v) { yearsExperience = v; }
        public String getLocation() { return location; }
        public void setLocation(String v) { location = v; }
        public String getPrimaryTools() { return primaryTools; }
        public void setPrimaryTools(String v) { primaryTools = v; }
        public boolean isPublicProfile() { return publicProfile; }
        public void setPublicProfile(boolean v) { publicProfile = v; }
    }

    /** One row of the public directory. */
    public record Listed(Person person, AiScore score, long sessions) {}

    private final PersonService people;
    private final PersonRepository repo;
    private final StatsService stats;
    private final ReportService reports;
    private final SessionService sessions;

    public ProfileController(PersonService people, PersonRepository repo, StatsService stats, ReportService reports, SessionService sessions) {
        this.people = people;
        this.repo = repo;
        this.stats = stats;
        this.reports = reports;
        this.sessions = sessions;
    }

    /** The open directory: every public individual profile, searchable, each linking to its profile page. */
    @GetMapping({"/p", "/profiles"})
    public String directory(@RequestParam(required = false) String q, Model model) {
        fillDirectory(q, model);
        return "profiles";
    }

    @GetMapping("/p/{handle}")
    public String publicProfile(@PathVariable String handle, Model model) {
        Person p = people.byHandle(handle).orElse(null);
        boolean owner = p != null && people.current().map(me -> me.getId().equals(p.getId())).orElse(false);
        if (p == null || p.isCompany() || (!p.isPublicProfile() && !owner)) {
            // Not a dead end: show the directory with a note about the handle that was asked for.
            model.addAttribute("missingHandle", handle);
            fillDirectory(null, model);
            return "profiles";
        }
        LocalDate today = LocalDate.now();
        List<MonthSummary> trend = stats.trend(p, YearMonth.from(today), 6);
        List<MonthlyReport> history = reports.history(p);
        model.addAttribute("person", p);
        model.addAttribute("owner", owner);
        model.addAttribute("score", stats.rollingScore(p, today));
        model.addAttribute("trend", trend);
        model.addAttribute("trendPath", Charts.linePath(trend.stream().map(m -> m.score().hasScore() ? m.score().composite() : -1).toList(), 1000, 320, 80));
        model.addAttribute("reportCount", history.size());
        model.addAttribute("sessionCount", sessions.count(p));
        model.addAttribute("since", sessions.firstSessionDate(p).orElse(null));
        model.addAttribute("tools", p.getPrimaryTools() == null ? List.of() : List.of(p.getPrimaryTools().split("\\s*,\\s*")));
        return "profile";
    }

    private void fillDirectory(String q, Model model) {
        LocalDate today = LocalDate.now();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Listed> list = repo.findByAccountTypeAndPublicProfileTrue(AccountType.INDIVIDUAL).stream()
                .filter(p -> needle.isEmpty() || haystack(p).contains(needle))
                .map(p -> new Listed(p, stats.rollingScore(p, today), sessions.count(p)))
                .sorted(Comparator.comparingInt((Listed l) -> l.score().hasScore() ? l.score().composite() : -1).reversed()
                        .thenComparing(l -> l.person().getDisplayName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        model.addAttribute("profiles", list);
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("total", repo.findByAccountTypeAndPublicProfileTrue(AccountType.INDIVIDUAL).size());
    }

    private static String haystack(Person p) {
        return String.join(" ", nz(p.getDisplayName()), nz(p.getHandle()), nz(p.getHeadline()), nz(p.getJobTitle()),
                nz(p.getIndustry()), nz(p.getLocation()), nz(p.getPrimaryTools())).toLowerCase(Locale.ROOT);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    @GetMapping("/settings")
    public String settings(Model model) {
        Person me = people.requireCurrent();
        model.addAttribute("form", ProfileForm.from(me));
        return "settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@Valid @ModelAttribute("form") ProfileForm form, BindingResult binding, RedirectAttributes redirect) {
        Person me = people.requireCurrent();
        if (!binding.hasErrors() && !form.getHandle().equals(me.getHandle())
                && people.byHandle(form.getHandle()).isPresent()) {
            binding.rejectValue("handle", "taken", "That handle is taken");
        }
        if (binding.hasErrors()) return "settings";
        form.applyTo(me);
        people.save(me);
        redirect.addFlashAttribute("flash", "Profile saved.");
        return "redirect:/settings";
    }

    @PostMapping("/settings/api-key")
    public String rotateKey(RedirectAttributes redirect) {
        people.rotateApiKey(people.requireCurrent());
        redirect.addFlashAttribute("flash", "New API key issued. Update the extension and any scripts.");
        return "redirect:/settings";
    }
}
