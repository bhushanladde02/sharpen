package io.sharpen.web;

import io.sharpen.domain.Person;
import io.sharpen.service.PdfService;
import io.sharpen.service.PersonService;
import io.sharpen.service.ReportModel;
import io.sharpen.service.ReportService;
import io.sharpen.service.SessionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final PersonService people;
    private final ReportService reports;
    private final PdfService pdf;
    private final SessionService sessions;

    public ReportController(PersonService people, ReportService reports, PdfService pdf, SessionService sessions) {
        this.people = people;
        this.reports = reports;
        this.pdf = pdf;
        this.sessions = sessions;
    }

    @GetMapping
    public String index(Model model) {
        Person me = people.requireCurrent();
        YearMonth current = YearMonth.from(LocalDate.now());
        YearMonth first = sessions.firstSessionDate(me).map(YearMonth::from).orElse(current);
        List<String> months = new ArrayList<>();
        for (YearMonth m = current; !m.isBefore(first) && months.size() < 24; m = m.minusMonths(1)) months.add(m.toString());
        model.addAttribute("months", months);
        model.addAttribute("history", reports.history(me));
        model.addAttribute("current", current.toString());
        return "reports";
    }

    /** Stored version if it exists, otherwise a live preview (not stored until generated). */
    @GetMapping("/{ym}")
    public String view(@PathVariable String ym, Model model) {
        Person me = people.requireCurrent();
        YearMonth month = YearMonth.parse(ym);
        Optional<ReportModel> stored = reports.stored(me, month);
        model.addAttribute("r", stored.orElseGet(() -> reports.build(me, month)));
        model.addAttribute("stored", stored.isPresent());
        return "report";
    }

    @PostMapping("/{ym}/generate")
    public String generate(@PathVariable String ym, RedirectAttributes redirect) {
        reports.generate(people.requireCurrent(), YearMonth.parse(ym));
        redirect.addFlashAttribute("flash", "Report for " + ym + " generated.");
        return "redirect:/reports/" + ym;
    }

    @GetMapping(value = "/{ym}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String ym) {
        Person me = people.requireCurrent();
        YearMonth month = YearMonth.parse(ym);
        ReportModel model = reports.stored(me, month).orElseGet(() -> reports.build(me, month));
        byte[] bytes = pdf.monthlyReport(model);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sharpen-" + me.getHandle() + "-" + ym + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
