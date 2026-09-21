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
        // A person gives first / middle (optional) / last; a company gives its name. Which set is required
        // depends on accountType, so that check is done in the controller rather than by annotations.
        @Size(max = 60) private String firstName = "";
        @Size(max = 60) private String middleName = "";
        @Size(max = 60) private String lastName = "";
        @Size(max = 120) private String companyName = "";
        @NotBlank @Email @Size(max = 190) private String email = "";
        @NotBlank @Size(min = 8, max = 72) private String password = "";
        private AccountType accountType = AccountType.INDIVIDUAL;

        public String getFirstName() { return firstName; }
        public void setFirstName(String v) { firstName = v; }
        public String getMiddleName() { return middleName; }
        public void setMiddleName(String v) { middleName = v; }
        public String getLastName() { return lastName; }
        public void setLastName(String v) { lastName = v; }
        public String getCompanyName() { return companyName; }
        public void setCompanyName(String v) { companyName = v; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public AccountType getAccountType() { return accountType; }
        public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    }

    private final PersonService people;

    public AuthController(PersonService people,
                          @org.springframework.beans.factory.annotation.Value("${sharpen.site-host:localhost:8080}") String siteHost) {
        this.people = people;
        String base = (siteHost.startsWith("localhost") ? "http://" : "https://") + siteHost;
        this.structuredData = STRUCTURED_DATA_TEMPLATE.formatted(base, GlobalModelAttributes.DEFAULT_DESCRIPTION);
    }

    /** A representative score for the landing-page preview; not a real person. */
    static final AiScore SAMPLE_SCORE = new AiScore(742, 68, 81, 84, 57, 92, 41, 39, AiScore.Confidence.FULL, false,
            List.of(new AiScore.Flag("strength", "You lead the work and check the output — the pattern employers pay for.")));

    /** JSON-LD for search engines: what Sharpen is, who made it, that it is free and open source. */
    private final String structuredData;

    private static final String STRUCTURED_DATA_TEMPLATE = """
        {"@context":"https://schema.org","@graph":[
          {"@type":"WebSite","@id":"%1$s/#website","url":"%1$s/",
           "name":"Sharpen","description":"%2$s","inLanguage":"en"},
          {"@type":"SoftwareApplication","@id":"%1$s/#app","name":"Sharpen",
           "alternateName":"Sharpen AI profile","applicationCategory":"BusinessApplication","operatingSystem":"Web",
           "url":"%1$s/","description":"%2$s",
           "offers":{"@type":"Offer","price":"0","priceCurrency":"USD"},
           "license":"https://www.gnu.org/licenses/agpl-3.0.html",
           "codeRepository":"https://github.com/bhushanladde02/sharpen",
           "featureList":["Log AI sessions in 15 seconds","Monthly AI usage report (PDF)","AI score 0–1000 across independence, effectiveness, verification, growth and breadth","Public AI profile for employers","Directory of public profiles","Browser extension and CSV/API import"],
           "author":{"@id":"%1$s/#author"}},
          {"@type":"Person","@id":"%1$s/#author","name":"Bhushan Arun Ladde",
           "url":"https://github.com/bhushanladde02","jobTitle":"Senior Software Engineer"}
        ]}
        """;

    @GetMapping("/")
    public String home(Model model) {
        if (people.current().isPresent()) return "redirect:/dashboard";
        model.addAttribute("sample", SAMPLE_SCORE);
        model.addAttribute("ogTitle", "Sharpen — the AI profile: measure how you work with AI, not how much");
        model.addAttribute("structuredData", structuredData);
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
        AccountType type = form.getAccountType() == null ? AccountType.INDIVIDUAL : form.getAccountType();
        if (type == AccountType.COMPANY) {
            if (form.getCompanyName().isBlank()) binding.rejectValue("companyName", "required", "Company name is required");
        } else {
            if (form.getFirstName().isBlank()) binding.rejectValue("firstName", "required", "First name is required");
            if (form.getLastName().isBlank()) binding.rejectValue("lastName", "required", "Last name is required");
        }
        if (binding.hasErrors()) return "register";
        try {
            String name = type == AccountType.COMPANY ? form.getCompanyName()
                    : io.sharpen.domain.Person.joinName(form.getFirstName(), form.getMiddleName(), form.getLastName());
            people.register(form.getEmail(), form.getPassword(), name, type);
        } catch (IllegalArgumentException e) {
            binding.rejectValue("email", "exists", e.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }
}
