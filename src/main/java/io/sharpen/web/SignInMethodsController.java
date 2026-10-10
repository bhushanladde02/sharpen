package io.sharpen.web;

import io.sharpen.auth.SignInService;
import io.sharpen.auth.SocialRegistrations;
import io.sharpen.auth.SocialUserServices;
import io.sharpen.domain.Person;
import io.sharpen.service.PersonService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Settings → Sign-in methods: connect a Google, GitHub or LinkedIn account to the signed-in person, or disconnect it.
 * Connecting is a POST (so it carries the form's anti-forgery token) that marks the session and hands over to
 * Spring's {@code /oauth2/authorization/<provider>}; {@code SocialUserServices} sees the mark when the provider
 * sends the person back and links instead of signing in someone new.
 */
@Controller
public class SignInMethodsController {

    private final PersonService people;
    private final SignInService signIn;
    private final SocialRegistrations social;

    public SignInMethodsController(PersonService people, SignInService signIn, SocialRegistrations social) {
        this.people = people;
        this.signIn = signIn;
        this.social = social;
    }

    @PostMapping("/settings/connect/{provider}")
    public String connect(@PathVariable String provider, HttpSession session, RedirectAttributes redirect) {
        Person me = people.requireCurrent();
        if (!social.offers(provider)) {
            redirect.addFlashAttribute("flash", "That sign-in method is not available here.");
            return "redirect:/settings#sign-in";
        }
        session.setAttribute(SocialUserServices.LINK_ATTRIBUTE, me.getId());
        return "redirect:/oauth2/authorization/" + provider;
    }

    @PostMapping("/settings/disconnect/{provider}")
    public String disconnect(@PathVariable String provider, RedirectAttributes redirect) {
        String problem = signIn.disconnect(people.requireCurrent(), provider);
        String label = social.label(provider);
        redirect.addFlashAttribute("flash", problem == null ? label + " disconnected." : label + " not disconnected — " + problem + ".");
        return "redirect:/settings#sign-in";
    }
}
