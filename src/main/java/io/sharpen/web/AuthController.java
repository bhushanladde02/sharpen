package io.sharpen.web;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.scoring.AiScore;
import io.sharpen.service.PersonService;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    public static class RegisterForm {
        @NotBlank @Size(max = 120) private String displayName = "";
        @NotBlank @Email @Size(max = 190) private String email = "";
        @NotBlank @Size(min = 8, max = 72) private String password = "";
        private AccountType accountType = AccountType.INDIVIDUAL;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public AccountType getAccountType() { return accountType; }
        public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    }

    private final PersonService people;

    public AuthController(PersonService people) {
        this.people = people;
    }

    /** A representative score for the landing-page preview; not a real person. */
    static final AiScore SAMPLE_SCORE = new AiScore(742, 68, 81, 84, 57, 92, 41, 39, AiScore.Confidence.FULL, false,
            List.of(new AiScore.Flag("strength", "You lead the work and check the output — the pattern employers pay for.")));

    /** JSON-LD for search engines: what Sharpen is, who made it, that it is free and open source. */
    static final String STRUCTURED_DATA = """
        {"@context":"https://schema.org","@graph":[
          {"@type":"WebSite","@id":"https://sharpen-ai.duckdns.org/#website","url":"https://sharpen-ai.duckdns.org/",
           "name":"Sharpen","description":"%s","inLanguage":"en"},
          {"@type":"SoftwareApplication","@id":"https://sharpen-ai.duckdns.org/#app","name":"Sharpen",
           "alternateName":"Sharpen AI profile","applicationCategory":"BusinessApplication","operatingSystem":"Web",
           "url":"https://sharpen-ai.duckdns.org/","description":"%s",
           "offers":{"@type":"Offer","price":"0","priceCurrency":"USD"},
           "license":"https://www.gnu.org/licenses/agpl-3.0.html",
           "codeRepository":"https://github.com/bhushanladde02/sharpen",
           "featureList":["Log AI sessions in 15 seconds","Monthly AI usage report (PDF)","AI score 0–1000 across independence, effectiveness, verification, growth and breadth","Public AI profile for employers","Directory of public profiles","Browser extension and CSV/API import"],
           "author":{"@id":"https://sharpen-ai.duckdns.org/#author"}},
          {"@type":"Person","@id":"https://sharpen-ai.duckdns.org/#author","name":"Bhushan Arun Ladde",
           "url":"https://github.com/bhushanladde02","jobTitle":"Senior Software Engineer"}
        ]}
        """.formatted(GlobalModelAttributes.DEFAULT_DESCRIPTION, GlobalModelAttributes.DEFAULT_DESCRIPTION);

    @GetMapping("/")
    public String home(Model model) {
        if (people.current().isPresent()) return "redirect:/dashboard";
        model.addAttribute("sample", SAMPLE_SCORE);
        model.addAttribute("ogTitle", "Sharpen — the AI profile: measure how you work with AI, not how much");
        model.addAttribute("structuredData", STRUCTURED_DATA);
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult binding) {
        if (binding.hasErrors()) return "register";
        try {
            people.register(form.getEmail(), form.getPassword(), form.getDisplayName(),
                    form.getAccountType() == null ? AccountType.INDIVIDUAL : form.getAccountType());
        } catch (IllegalArgumentException e) {
            binding.rejectValue("email", "exists", e.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }
}
