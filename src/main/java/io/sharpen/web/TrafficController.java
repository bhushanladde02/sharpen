package io.sharpen.web;

import io.sharpen.domain.Person;
import io.sharpen.service.CountryLookup;
import io.sharpen.service.PdfService;
import io.sharpen.service.PersonService;
import io.sharpen.service.TrafficService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * The owner's traffic dashboard and the evidence exports (CSV of the daily series, PDF summary). Visible only
 * to the account named in {@code sharpen.admin-email}, like the feedback inbox.
 */
@Controller
public class TrafficController {

    private final TrafficService traffic;
    private final PersonService people;
    private final PdfService pdf;
    private final CountryLookup countries;
    private final io.sharpen.repo.PageViewRepository views;
    private final String adminEmail;

    /**
     * The growth target the owner set on 12 October 2026 (see the pilot log, day 27): members and completed
     * habits checks by a date. Shown on the Traffic page with progress, so the number is never out of sight.
     */
    public record Goal(LocalDate from, LocalDate to, long membersTarget, long members, long checksTarget, long checks,
                       long daysLeft) {
        public int membersPercent() { return (int) Math.min(100, 100 * members / Math.max(1, membersTarget)); }
        public int checksPercent() { return (int) Math.min(100, 100 * checks / Math.max(1, checksTarget)); }
    }
    static final LocalDate GOAL_FROM = LocalDate.of(2026, 10, 12), GOAL_TO = LocalDate.of(2026, 11, 12);
    static final long GOAL_MEMBERS = 50, GOAL_CHECKS = 500;

    /** One slice of the "where visitors come from" pie; {@code slot} picks its colour (1–5, or "other"). */
    public record Slice(String label, long visitors, String percent, String path, String slot) {}

    /** Five named countries at most, the rest as one "Other countries" slice — a pie stays readable up to six. */
    static final int PIE_COUNTRIES = 5;

    public TrafficController(TrafficService traffic, PersonService people, PdfService pdf, CountryLookup countries,
                             io.sharpen.repo.PageViewRepository views, @Value("${sharpen.admin-email:}") String adminEmail) {
        this.traffic = traffic;
        this.people = people;
        this.pdf = pdf;
        this.countries = countries;
        this.views = views;
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim().toLowerCase(Locale.ROOT);
    }

    private void requireAdmin() {
        Person me = people.requireCurrent();
        if (adminEmail.isEmpty() || !adminEmail.equals(me.getEmail().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** Resolve the period: a month ({@code ?month=2026-09}), a number of days ({@code ?days=30}), or everything. */
    private LocalDate[] range(String month, Integer days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (month != null && !month.isBlank()) {
            YearMonth ym = YearMonth.parse(month);
            LocalDate to = ym.atEndOfMonth().isAfter(today) ? today : ym.atEndOfMonth();
            return new LocalDate[]{ym.atDay(1), to};
        }
        if (days != null && days > 0) return new LocalDate[]{today.minusDays(days - 1L), today};
        return new LocalDate[]{traffic.firstDay(), today};
    }

    @GetMapping("/admin/traffic")
    public String dashboard(@RequestParam(required = false) String month, @RequestParam(required = false) Integer days, Model model) {
        requireAdmin();
        // Default to the last 30 days when neither a month nor a day count is given. Both branches must stay
        // Integer: with a bare "30" the ternary unboxes "days" and a month-only request throws an NPE.
        Integer effectiveDays = days == null && (month == null || month.isBlank()) ? Integer.valueOf(30) : days;
        LocalDate[] r = range(month, effectiveDays);
        TrafficService.Summary s = traffic.summary(r[0], r[1]);
        model.addAttribute("s", s);
        model.addAttribute("month", month == null ? "" : month);
        model.addAttribute("days", effectiveDays);
        int max = (int) Math.max(1, s.days().stream().mapToLong(TrafficService.Day::views).max().orElse(1));
        model.addAttribute("viewsPath", Charts.linePath(s.days().stream().map(d -> (int) d.views()).toList(), max, 640, 160));
        model.addAttribute("visitorsPath", Charts.linePath(s.days().stream().map(d -> (int) d.visitors()).toList(), max, 640, 160));
        model.addAttribute("maxViews", max);
        List<String> months = new java.util.ArrayList<>();
        for (YearMonth ym = YearMonth.from(LocalDate.now(ZoneOffset.UTC)); !ym.isBefore(YearMonth.from(traffic.firstDay())); ym = ym.minusMonths(1)) months.add(ym.toString());
        model.addAttribute("months", months);
        model.addAttribute("pie", pie(s.places()));
        model.addAttribute("geo", countries.status());
        long checks = ((Number) views.checkResults().get(0)[1]).longValue();
        model.addAttribute("goal", new Goal(GOAL_FROM, GOAL_TO, GOAL_MEMBERS, s.members(), GOAL_CHECKS, checks,
                Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), GOAL_TO))));
        return "admin-traffic";
    }

    static List<Slice> pie(TrafficService.Places places) {
        List<TrafficService.Place> list = places.countries();
        int named = list.size() <= PIE_COUNTRIES ? list.size() : PIE_COUNTRIES;
        List<Long> values = new java.util.ArrayList<>();
        for (int i = 0; i < named; i++) values.add(list.get(i).visitors());
        long rest = 0;
        for (int i = named; i < list.size(); i++) rest += list.get(i).visitors();
        if (rest > 0) values.add(rest);
        List<String> paths = Charts.pieSlices(values, 90);
        List<Slice> out = new java.util.ArrayList<>();
        for (int i = 0; i < named; i++) {
            var p = list.get(i);
            out.add(new Slice(p.name(), p.visitors(), p.percent(), paths.get(i), String.valueOf(i + 1)));
        }
        if (rest > 0) {
            int others = list.size() - named;
            var share = new TrafficService.Place("", "", 0, rest, places.located() == 0 ? 0 : rest / (double) places.located());
            out.add(new Slice("Other countries (" + others + ")", rest, share.percent(), paths.get(named), "other"));
        }
        return out;
    }

    @GetMapping(value = "/admin/traffic.csv", produces = "text/csv")
    public ResponseEntity<String> csv(@RequestParam(required = false) String month, @RequestParam(required = false) Integer days) {
        requireAdmin();
        LocalDate[] r = range(month, days);
        TrafficService.Summary s = traffic.summary(r[0], r[1]);
        String name = "sharpen-traffic-" + (month != null && !month.isBlank() ? month : r[0] + "_" + r[1]) + ".csv";
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("text/csv")).body(traffic.csv(s));
    }

    @GetMapping(value = "/admin/traffic.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdfReport(@RequestParam(required = false) String month, @RequestParam(required = false) Integer days) {
        requireAdmin();
        LocalDate[] r = range(month, days);
        TrafficService.Summary s = traffic.summary(r[0], r[1]);
        String name = "sharpen-traffic-" + (month != null && !month.isBlank() ? month : r[0] + "_" + r[1]) + ".pdf";
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.APPLICATION_PDF).body(pdf.trafficReport(s));
    }
}
