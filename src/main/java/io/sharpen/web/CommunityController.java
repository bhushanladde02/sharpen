package io.sharpen.web;

import io.sharpen.domain.Person;
import io.sharpen.service.CommunityService;
import io.sharpen.service.CommunityStats;
import io.sharpen.service.PersonService;
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
    private final String adminEmail;

    public CommunityController(CommunityService community, PersonService people,
                               @Value("${sharpen.admin-email:}") String adminEmail) {
        this.community = community;
        this.people = people;
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
        people.current().ifPresent(p -> f.setEmail(p.getEmail()));
        model.addAttribute("form", f);
        return "feedback";
    }

    @PostMapping("/feedback")
    public String submit(@Valid @ModelAttribute("form") FeedbackForm form, BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) return "feedback";
        Optional<Person> me = people.current();
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
        model.addAttribute("items", community.latestFeedback());
        model.addAttribute("stats", community.stats());
        return "admin-feedback";
    }
}
