package io.sharpen.web;

import io.sharpen.domain.Person;
import io.sharpen.scoring.AiScore;
import io.sharpen.service.PersonService;
import io.sharpen.service.StatsService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * The share loop. {@code /p/<handle>/badge.svg} is a small picture of a person's current AI score that they can
 * drop into a GitHub README, a LinkedIn post or an e-mail signature; every one of them links back to the profile.
 * It shows only what the public profile already shows, and only for profiles that are public — a private
 * profile's badge is a 404, so nothing leaks through a hotlinked image. Rendered fresh every hour at most.
 */
@Controller
public class BadgeController {

    private final PersonService people;
    private final StatsService stats;

    public BadgeController(PersonService people, StatsService stats) {
        this.people = people;
        this.stats = stats;
    }

    @GetMapping(value = "/p/{handle}/badge.svg", produces = "image/svg+xml")
    public ResponseEntity<byte[]> badge(@PathVariable String handle) {
        Person p = people.byHandle(handle).orElse(null);
        if (p == null || p.isCompany() || !p.isPublicProfile()) return ResponseEntity.notFound().build();
        AiScore score = stats.rollingScore(p, LocalDate.now());
        String svg = render(p.getDisplayName(), score);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml; charset=UTF-8"))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(svg.getBytes(StandardCharsets.UTF_8));
    }

    /** 300×64, flat, readable on light and dark backgrounds; the number is the only big thing. */
    static String render(String name, AiScore score) {
        boolean scored = score.hasScore();
        String number = scored ? Integer.toString(score.composite()) : "—";
        String band = scored ? score.band() : "not yet scored";
        String accent = !scored ? "#7c8794" : score.composite() >= 800 ? "#1f9d55" : score.composite() >= 650 ? "#2857d9"
                : score.composite() >= 500 ? "#c98a00" : "#c0392b";
        String who = esc(name.length() > 22 ? name.substring(0, 21) + "…" : name);
        return """
            <svg xmlns="http://www.w3.org/2000/svg" width="300" height="64" viewBox="0 0 300 64" role="img" aria-label="Sharpen AI score %1$s, %2$s, %3$s">
              <title>Sharpen AI score %1$s — %2$s — %3$s</title>
              <rect width="300" height="64" rx="12" fill="#12171f"/>
              <rect x="0.5" y="0.5" width="299" height="63" rx="11.5" fill="none" stroke="#2a3340"/>
              <rect x="14" y="17" width="30" height="30" rx="8" fill="#2857d9"/>
              <path d="M21 39V30h7v-7h9v9h-7v7z" fill="#fff"/>
              <text x="56" y="26" font-family="ui-sans-serif,system-ui,-apple-system,Segoe UI,Helvetica,Arial,sans-serif" font-size="10" font-weight="700" fill="#7c8794" letter-spacing="1.1">SHARPEN AI SCORE</text>
              <text x="56" y="47" font-family="ui-sans-serif,system-ui,-apple-system,Segoe UI,Helvetica,Arial,sans-serif" font-size="13" font-weight="600" fill="#e6eaf0">%3$s</text>
              <text x="286" y="45" text-anchor="end" font-family="ui-sans-serif,system-ui,-apple-system,Segoe UI,Helvetica,Arial,sans-serif" font-size="28" font-weight="800" fill="%4$s">%1$s</text>
              <text x="286" y="19" text-anchor="end" font-family="ui-sans-serif,system-ui,-apple-system,Segoe UI,Helvetica,Arial,sans-serif" font-size="10" font-weight="700" fill="%4$s" letter-spacing=".5">%2$s</text>
            </svg>
            """.formatted(number, esc(band), who, accent);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
