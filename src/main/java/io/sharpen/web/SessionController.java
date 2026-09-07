package io.sharpen.web;

import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.Person;
import io.sharpen.domain.UsageSession;
import io.sharpen.service.PersonService;
import io.sharpen.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/sessions")
public class SessionController {

    static final List<String> COMMON_TOOLS = List.of("Claude", "ChatGPT", "Gemini", "GitHub Copilot", "Cursor",
            "Claude Code", "Perplexity", "Microsoft Copilot", "Other");

    private final PersonService people;
    private final SessionService sessions;

    public SessionController(PersonService people, SessionService sessions) {
        this.people = people;
        this.sessions = sessions;
    }

    @ModelAttribute
    public void options(Model model) {
        model.addAttribute("contexts", UsageContext.values());
        model.addAttribute("categories", TaskCategory.values());
        model.addAttribute("tools", COMMON_TOOLS);
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        Person me = people.requireCurrent();
        model.addAttribute("page", sessions.page(me, page, 25));
        model.addAttribute("unrated", sessions.needingAssessment(me, 5));
        return "sessions";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("form", new SessionForm());
        model.addAttribute("mode", "new");
        return "session-form";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("form") SessionForm form, BindingResult binding, Model model,
                       RedirectAttributes redirect) {
        if (binding.hasErrors()) { model.addAttribute("mode", "new"); return "session-form"; }
        sessions.logManual(people.requireCurrent(), form.toInput());
        redirect.addFlashAttribute("flash", "Session logged.");
        return "redirect:/dashboard";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        UsageSession s = sessions.find(people.requireCurrent(), id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        model.addAttribute("form", SessionForm.from(s));
        model.addAttribute("session", s);
        model.addAttribute("mode", s.isSelfAssessed() ? "edit" : "rate");
        return "session-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") SessionForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect, @RequestParam(required = false) String next) {
        Person me = people.requireCurrent();
        if (binding.hasErrors()) {
            model.addAttribute("session", sessions.find(me, id).orElse(null));
            model.addAttribute("mode", "edit");
            return "session-form";
        }
        sessions.assess(me, id, form.toInput());
        redirect.addFlashAttribute("flash", "Session saved.");
        if ("rate".equals(next)) {
            List<UsageSession> more = sessions.needingAssessment(me, 1);
            if (!more.isEmpty()) return "redirect:/sessions/" + more.get(0).getId() + "/edit";
        }
        return "redirect:/sessions";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        sessions.delete(people.requireCurrent(), id);
        redirect.addFlashAttribute("flash", "Session deleted.");
        return "redirect:/sessions";
    }
}
