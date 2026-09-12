package io.sharpen.web;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.MonthlyReport;
import io.sharpen.domain.Person;
import io.sharpen.repo.PersonRepository;
import io.sharpen.scoring.AiScore;
import io.sharpen.domain.PersonAvatar;
import io.sharpen.service.AvatarService;
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
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

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
    public record Listed(Person person, AiScore score, long sessions, String searchText, List<String> tools) {}

    private final PersonService people;
    private final PersonRepository repo;
    private final StatsService stats;
    private final ReportService reports;
    private final SessionService sessions;
    private final AvatarService avatars;

    public ProfileController(PersonService people, PersonRepository repo, StatsService stats, ReportService reports,
                             SessionService sessions, AvatarService avatars) {
        this.people = people;
        this.repo = repo;
        this.stats = stats;
        this.reports = reports;
        this.sessions = sessions;
        this.avatars = avatars;
    }

    /** True when {@code viewer} may see {@code p}'s profile: public, or the owner looking at their own. */
    private boolean visible(Person p) {
        if (p == null) return false;
        if (people.current().map(me -> me.getId().equals(p.getId())).orElse(false)) return true;
        return !p.isCompany() && p.isPublicProfile();
    }

    /** The picture itself. The URL carries {@code ?v=<version>}, so it can be cached for a year. */
    @GetMapping("/p/{handle}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable String handle) {
        Person p = people.byHandle(handle).orElse(null);
        if (!visible(p)) return ResponseEntity.notFound().build();
        PersonAvatar a = avatars.find(p).orElse(null);
        if (a == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .eTag("\"" + p.getAvatarVersion() + "\"")
                .body(a.getBytes());
    }

    @PostMapping("/settings/avatar")
    public String uploadAvatar(@RequestParam("picture") MultipartFile picture, RedirectAttributes redirect) {
        Person me = people.requireCurrent();
        try {
            avatars.store(me, picture);
            redirect.addFlashAttribute("flash", "Picture updated.");
        } catch (AvatarService.InvalidImage e) {
            redirect.addFlashAttribute("flash", e.getMessage());
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/avatar/remove")
    public String removeAvatar(RedirectAttributes redirect) {
        avatars.remove(people.requireCurrent());
        redirect.addFlashAttribute("flash", "Picture removed.");
        return "redirect:/settings";
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
        model.addAttribute("tools", stats.tools(p));
        return "profile";
    }

    /**
     * Every public profile goes to the page; searching, filtering and sorting happen in the browser so results
     * update as the visitor types. {@code q} only pre-fills the box (so links like {@code /p?q=claude} work).
     */
    private void fillDirectory(String q, Model model) {
        LocalDate today = LocalDate.now();
        List<Person> all = repo.findByAccountTypeAndPublicProfileTrue(AccountType.INDIVIDUAL);
        List<Listed> list = all.stream()
                .map(p -> {
                    List<String> tools = stats.tools(p).stream().map(StatsService.ToolUse::name).toList();
                    return new Listed(p, stats.rollingScore(p, today), sessions.count(p), haystack(p, tools), tools);
                })
                .sorted(Comparator.comparingInt((Listed l) -> l.score().hasScore() ? l.score().composite() : -1).reversed()
                        .thenComparing(l -> l.person().getDisplayName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        Set<String> tools = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> industries = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Listed l : list) {
            tools.addAll(l.tools());
            if (l.person().getIndustry() != null && !l.person().getIndustry().isBlank()) industries.add(l.person().getIndustry().trim());
        }
        model.addAttribute("profiles", list);
        model.addAttribute("q", q == null ? "" : q.trim());
        model.addAttribute("total", all.size());
        model.addAttribute("toolChips", tools);
        model.addAttribute("industryChips", industries);
    }

    /** Lower-cased text the browser-side search matches against. */
    private static String haystack(Person p, List<String> tools) {
        return String.join(" ", nz(p.getDisplayName()), nz(p.getHandle()), nz(p.getHeadline()), nz(p.getJobTitle()),
                nz(p.getIndustry()), nz(p.getLocation()), String.join(" ", tools)).toLowerCase(Locale.ROOT);
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
