package io.sharpen.web;

import io.sharpen.domain.Person;
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
    private final String adminEmail;

    public TrafficController(TrafficService traffic, PersonService people, PdfService pdf,
                             @Value("${sharpen.admin-email:}") String adminEmail) {
        this.traffic = traffic;
        this.people = people;
        this.pdf = pdf;
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
        LocalDate[] r = range(month, days == null && (month == null || month.isBlank()) ? 30 : days);
        TrafficService.Summary s = traffic.summary(r[0], r[1]);
        model.addAttribute("s", s);
        model.addAttribute("month", month == null ? "" : month);
        model.addAttribute("days", days == null && (month == null || month.isBlank()) ? 30 : days);
        int max = (int) Math.max(1, s.days().stream().mapToLong(TrafficService.Day::views).max().orElse(1));
        model.addAttribute("viewsPath", Charts.linePath(s.days().stream().map(d -> (int) d.views()).toList(), max, 640, 160));
        model.addAttribute("visitorsPath", Charts.linePath(s.days().stream().map(d -> (int) d.visitors()).toList(), max, 640, 160));
        model.addAttribute("maxViews", max);
        List<String> months = new java.util.ArrayList<>();
        for (YearMonth ym = YearMonth.from(LocalDate.now(ZoneOffset.UTC)); !ym.isBefore(YearMonth.from(traffic.firstDay())); ym = ym.minusMonths(1)) months.add(ym.toString());
        model.addAttribute("months", months);
        return "admin-traffic";
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
