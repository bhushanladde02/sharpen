package io.sharpen.web;

import io.sharpen.domain.Person;
import io.sharpen.service.InviteService;
import io.sharpen.service.PersonService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * {@code /invite}: a member's invitation, ready to send from their own mail app, the clipboard, LinkedIn, WhatsApp
 * or X — and, where mail is switched on, from Sharpen itself. {@code /join/<handle>} is the link every invitation
 * carries; {@code /invite/unsubscribe/<token>} is the one-click opt-out in every email Sharpen sends.
 */
@Controller
public class InviteController {

    private final InviteService invites;
    private final PersonService people;

    public InviteController(InviteService invites, PersonService people) {
        this.invites = invites;
        this.people = people;
    }

    @GetMapping("/invite")
    public String page(@RequestParam(required = false) String friend, @RequestParam(required = false) String note, Model model) {
        Person me = people.requireCurrent();
        InviteService.Text text = invites.text(me, friend, note);
        String join = invites.joinLink(me);
        model.addAttribute("friend", friend == null ? "" : friend);
        model.addAttribute("note", note == null ? "" : note);
        model.addAttribute("noteMax", InviteService.NOTE_MAX);
        model.addAttribute("text", text);
        model.addAttribute("body", text.body());
        model.addAttribute("mailto", "mailto:?subject=" + enc(text.subject()) + "&body=" + enc(text.body()));
        model.addAttribute("joinLink", join);
        String share = invites.shareText(me);
        model.addAttribute("shareText", share);
        model.addAttribute("linkedinShare", "https://www.linkedin.com/sharing/share-offsite/?url=" + enc(join));
        model.addAttribute("whatsappShare", "https://wa.me/?text=" + enc(share));
        model.addAttribute("xShare", "https://twitter.com/intent/tweet?text=" + enc(share));
        model.addAttribute("mailEnabled", invites.mailEnabled());
        model.addAttribute("perDay", InviteService.PER_DAY);
        model.addAttribute("tally", invites.tally(me));
        model.addAttribute("pageDescription", "Invite a friend to Sharpen.");
        return "invite";
    }

    /** "Send from Sharpen": every rule in {@link InviteService#send} applies; the outcome comes back as a flash. */
    @PostMapping("/invite")
    public String send(@RequestParam String email, @RequestParam(required = false) String friend,
                       @RequestParam(required = false) String note, RedirectAttributes redirect) {
        Person me = people.requireCurrent();
        Optional<InviteService.Refusal> refusal = invites.send(me, email, friend, note);
        if (refusal.isPresent()) {
            redirect.addFlashAttribute("notice", refusal.get().message);
            redirect.addAttribute("friend", friend == null ? "" : friend);
            redirect.addAttribute("note", note == null ? "" : note);
        } else {
            redirect.addFlashAttribute("flash", "Invitation sent to " + email.trim() + ". You will see it here when they join.");
        }
        return "redirect:/invite";
    }

    /** The link in every invitation. A real handle lands on sign-up with the inviter remembered; anything else, plain sign-up. */
    @GetMapping("/join/{handle}")
    public String join(@PathVariable String handle) {
        if (handle.matches("[a-z0-9-]{1,60}") && people.byHandle(handle).isPresent()) return "redirect:/register?ref=" + handle;
        return "redirect:/register";
    }

    /** One click, no account, no confirmation step: the address behind the token is never written to again. */
    @GetMapping("/invite/unsubscribe/{token}")
    public String unsubscribe(@PathVariable String token, Model model) {
        model.addAttribute("ok", invites.unsubscribe(token));
        return "invite-unsubscribed";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
