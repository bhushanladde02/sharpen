package io.sharpen.web;

import io.sharpen.domain.Person;
import io.sharpen.service.MonthSummary;
import io.sharpen.service.PersonService;
import io.sharpen.service.SessionService;
import io.sharpen.service.StatsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Controller
public class DashboardController {

    private final PersonService people;
    private final StatsService stats;
    private final SessionService sessions;

    public DashboardController(PersonService people, StatsService stats, SessionService sessions) {
        this.people = people;
        this.stats = stats;
        this.sessions = sessions;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Person me = people.requireCurrent();
        if (me.isCompany()) return "redirect:/candidates";

        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);
        List<MonthSummary> trend = stats.trend(me, thisMonth, 6);

        model.addAttribute("score", stats.rollingScore(me, today));
        model.addAttribute("month", trend.get(trend.size() - 1));
        model.addAttribute("trend", trend);
        model.addAttribute("trendPath", Charts.linePath(trend.stream().map(m -> m.score().hasScore() ? m.score().composite() : -1).toList(), 1000, 320, 80));
        model.addAttribute("unrated", sessions.needingAssessment(me, 3));
        model.addAttribute("recent", sessions.page(me, 0, 6).getContent());
        model.addAttribute("total", sessions.count(me));
        return "dashboard";
    }
}
