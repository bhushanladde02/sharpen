package io.sharpen.web;

import io.sharpen.service.InsightsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * {@code /privacy}: what Sharpen stores, what it shows, what it never collects, and how to take it out or delete
 * it — written from the code, not from a template. The thresholds the page quotes come from the services that
 * enforce them, so the text cannot drift from the behaviour. Every change to the page is in the public Git
 * history of {@code templates/privacy.html}.
 */
@Controller
public class PrivacyController {

    /** Shown at the top of the page; bump it with any change to what the page promises. */
    static final String EFFECTIVE = "8 October 2026";

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("effective", EFFECTIVE);
        model.addAttribute("insightsMinPeople", InsightsService.MIN_PEOPLE);
        model.addAttribute("insightsMinPerTool", InsightsService.MIN_PEOPLE_PER_TOOL);
        model.addAttribute("insightsDays", InsightsService.WINDOW_DAYS);
        model.addAttribute("ogTitle", "Privacy — Sharpen");
        model.addAttribute("pageDescription", "What Sharpen stores, what it shows publicly, what it never collects (anything you type into an AI tool), "
                + "and how to export or delete your data yourself. No advertising, no third-party analytics, no data sold.");
        return "privacy";
    }
}
