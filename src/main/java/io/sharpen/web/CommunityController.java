package io.sharpen.web;

import io.sharpen.domain.Person;
import io.sharpen.service.CommunityService;
import io.sharpen.service.CommunityStats;
import io.sharpen.service.PersonService;
import io.sharpen.service.SpamGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Locale;
import java.util.Optional;

/** Public counters, the feedback form, and the owner's feedback inbox. */
@Controller
public class CommunityController {

    public static class FeedbackForm {
        @NotBlank @Size(max = 2000) private String message;
        @Email @Size(max = 190) private String email;
        @Min(1) @Max(5) private Integer rating;
        @Size(max = 200) private String page;
        private String website = "";   // honeypot: hidden from people, filled by bots
        private String t = "";         // signed timestamp from SpamGuard.token()

        public String getWebsite() { return website; }
        public void setWebsite(String v) { website = v; }
        public String getT() { return t; }
        public void setT(String v) { t = v; }
        public String getMessage() { return message; }
        public void setMessage(String v) { message = v; }
        public String getEmail() { return email; }
        public void setEmail(String v) { email = v; }
        public Integer getRating() { return rating; }
        public void setRating(Integer v) { rating = v; }
        public String getPage() { return page; }
        public void setPage(String v) { page = v; }
    }

    private final CommunityService community;
    private final PersonService people;
    private final SpamGuard spam;
    private final String adminEmail;

    public CommunityController(CommunityService community, PersonService people, SpamGuard spam,
                               @Value("${sharpen.admin-email:}") String adminEmail) {
        this.community = community;
        this.people = people;
        this.spam = spam;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
    }

    /** Open JSON used by the live counter on every page. */
    @GetMapping("/api/v1/public/stats")
    @ResponseBody
    public CommunityStats stats() {
        return community.stats();
    }

    @GetMapping("/feedback")
    public String form(@RequestParam(required = false) String from, Model model) {
        FeedbackForm f = new FeedbackForm();
        f.setPage(from);
        f.setT(spam.token());
        people.current().ifPresent(p -> f.setEmail(p.getEmail()));
        model.addAttribute("form", f);
        return "feedback";
    }

    @PostMapping("/feedback")
    public String submit(@Valid @ModelAttribute("form") FeedbackForm form, BindingResult binding, RedirectAttributes redirect,
                         HttpServletRequest request) {
        if (binding.hasErrors()) { form.setT(spam.token()); return "feedback"; }
        Optional<Person> me = people.current();
        SpamGuard.Refusal refusal = spam.check(form.getWebsite(), form.getT(), clientAddress(request));
        if (refusal == SpamGuard.Refusal.HONEYPOT) {
            // A bot filled the invisible field: pretend it worked, store nothing.
            return "redirect:" + (me.isPresent() && !me.get().isCompany() ? "/dashboard" : "/");
        }
        if (refusal != null) {
            binding.reject("spam", switch (refusal) {
                case TOO_FAST -> "That was quick — please take a moment and press Send again.";
                case RATE_LIMITED -> "That is a lot of messages in one hour — please try again later.";
                default -> "This form had been open too long — please press Send again.";
            });
            form.setT(spam.token());   // a fresh token so the resend goes through
            return "feedback";
        }
        community.leave(me.map(Person::getId).orElse(null), form.getEmail(), form.getRating(), form.getMessage(), form.getPage());
        redirect.addFlashAttribute("flash", "Thank you — every message is read.");
        return "redirect:" + (me.isPresent() && !me.get().isCompany() ? "/dashboard" : "/");
    }

    /** Only the account whose email matches {@code sharpen.admin-email} can read the inbox. */
    @GetMapping("/admin/feedback")
    public String inbox(Model model) {
        Person me = people.requireCurrent();
        if (adminEmail.isEmpty() || !adminEmail.equals(me.getEmail().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var all = community.latestFeedback();
        model.addAttribute("items", all.stream().filter(f -> !spam.looksLikeSpam(f.getMessage(), f.getEmail())).toList());
        model.addAttribute("spam", all.stream().filter(f -> spam.looksLikeSpam(f.getMessage(), f.getEmail())).toList());
        model.addAttribute("stats", community.stats());
        return "admin-feedback";
    }

    /** Caddy sets X-Forwarded-For in production; the first address is the client's. */
    private static String clientAddress(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
