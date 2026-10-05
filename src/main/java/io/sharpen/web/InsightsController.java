package io.sharpen.web;

import io.sharpen.service.InsightsService;
import io.sharpen.service.InsightsService.Insights;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * {@code /insights}: the community's AI use over the last 90 days as one public page — the thing a measurement
 * company publishes about its panel, published here about the people who chose to be measured. Open to
 * everyone; shows figures only once {@link InsightsService#MIN_PEOPLE} people are in them.
 */
@Controller
public class InsightsController {

    private final InsightsService insights;

    public InsightsController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping("/insights")
    public String insights(Model model) {
        Insights in = insights.current();
        model.addAttribute("in", in);
        model.addAttribute("minPeople", InsightsService.MIN_PEOPLE);
        model.addAttribute("minPeoplePerTool", InsightsService.MIN_PEOPLE_PER_TOOL);
        model.addAttribute("ogTitle", "AI usage insights — Sharpen");
        model.addAttribute("pageDescription", in.published()
                ? "How " + in.people() + " people on Sharpen actually used AI in the last 90 days: " + in.hours()
                  + " rated hours, " + in.professionalPercent() + "% professional, output checked in " + in.verifiedPercent() + "% of sessions."
                : "What the Sharpen community's rated AI sessions add up to over the last 90 days — hours per tool, work versus personal, how often output is checked. Published once " + InsightsService.MIN_PEOPLE + " people are in the numbers.");
        return "insights";
    }
}
