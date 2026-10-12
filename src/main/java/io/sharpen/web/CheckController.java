package io.sharpen.web;

import io.sharpen.service.HabitCheck;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The 60-second AI habits check: {@code /check} asks nine questions, {@code /check/result} turns the answers into
 * the shareable address {@code /check/r/<code>}, and that page shows the result with share links. No account, no
 * cookie, nothing stored — the answers are only in the address. Open to everyone; the result pages are kept out of
 * search engines (the question page is the one worth finding).
 */
@Controller
public class CheckController {

    @GetMapping("/check")
    public String questions(Model model) {
        model.addAttribute("questions", HabitCheck.QUESTIONS);
        model.addAttribute("ogTitle", "How sharp is your AI use? A 60-second check — Sharpen");
        model.addAttribute("pageDescription", "Nine questions, no sign-up: see how independently you work with AI, how "
                + "often you check its output, and whether you are learning or just getting things done. Free, private, "
                + "nothing stored.");
        return "check";
    }

    /** The form posts here with GET q1..q9; anything incomplete goes back to the questions. */
    @GetMapping("/check/result")
    public String result(@RequestParam Map<String, String> params) {
        StringBuilder code = new StringBuilder();
        for (int i = 1; i <= HabitCheck.QUESTIONS.size(); i++) {
            String v = params.get("q" + i);
            if (v == null || v.length() != 1 || v.charAt(0) < '0' || v.charAt(0) > '3') return "redirect:/check?incomplete";
            code.append(v);
        }
        return "redirect:/check/r/" + code;
    }

    @GetMapping("/check/r/{code}")
    public String shared(@PathVariable String code, Model model, HttpServletRequest request) {
        HabitCheck.Result r = HabitCheck.score(code);
        if (r == null) return "redirect:/check";
        String host = request.getHeader("Host");
        String url = "https://" + (host != null && !host.isBlank() ? host : request.getServerName()) + "/check/r/" + r.code();
        String title = HabitCheck.shareTitle(r);
        model.addAttribute("r", r);
        model.addAttribute("ogTitle", title);
        model.addAttribute("pageDescription", r.typeLine() + " Take the free 60-second check on Sharpen and see yours.");
        model.addAttribute("shareUrl", url);
        // Plain links, no third-party script: the browser goes to LinkedIn or X only if the person clicks.
        model.addAttribute("linkedinShare", "https://www.linkedin.com/sharing/share-offsite/?url=" + enc(url));
        model.addAttribute("xShare", "https://twitter.com/intent/tweet?text=" + enc(title + ". How sharp is yours?") + "&url=" + enc(url));
        return "check-result";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
