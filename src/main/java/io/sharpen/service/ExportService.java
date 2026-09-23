package io.sharpen.service;

import io.sharpen.domain.MonthlyReport;
import io.sharpen.domain.Person;
import io.sharpen.domain.UsageSession;
import io.sharpen.repo.MonthlyReportRepository;
import io.sharpen.repo.UsageSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a person has put into Sharpen, back out again: sessions as CSV in the same layout the importer
 * reads (so a file exported here can be imported into another account, or opened in a spreadsheet), and the
 * whole account — profile, sessions, generated reports — as one JSON document. Nothing is left out that the
 * person can see on screen; nothing is included that they cannot (no password hash, no API key).
 */
@Service
public class ExportService {

    /** Same column order as docs/samples/sessions.csv; the importer recognises the layout by "human_pct". */
    static final String CSV_HEADER = "date,context,tool,task,minutes,prompts,human_pct,verified,learned,outcome,notes,external_id,tokens_in,tokens_out,source,self_assessed";

    private final UsageSessionRepository sessions;
    private final MonthlyReportRepository reports;
    private final ReportService reportService;

    public ExportService(UsageSessionRepository sessions, MonthlyReportRepository reports, ReportService reportService) {
        this.sessions = sessions;
        this.reports = reports;
        this.reportService = reportService;
    }

    @Transactional(readOnly = true)
    public String sessionsCsv(Person person) {
        StringBuilder sb = new StringBuilder(CSV_HEADER).append('\n');
        for (UsageSession s : sessions.findByPersonIdOrderByOccurredOnAscIdAsc(person.getId())) {
            sb.append(s.getOccurredOn()).append(',')
              .append(s.getContext().name().toLowerCase()).append(',')
              .append(csv(s.getTool())).append(',')
              .append(s.getTaskCategory().name().toLowerCase()).append(',')
              .append(s.getDurationMinutes()).append(',')
              .append(s.getPromptCount()).append(',')
              .append(s.getHumanContributionPct()).append(',')
              .append(s.isVerifiedOutput() ? "yes" : "no").append(',')
              .append(s.isLearnedSomething() ? "yes" : "no").append(',')
              .append(s.getOutcome()).append(',')
              .append(csv(s.getNotes())).append(',')
              .append(csv(s.getExternalId())).append(',')
              .append(s.getTokensIn() == null ? "" : s.getTokensIn()).append(',')
              .append(s.getTokensOut() == null ? "" : s.getTokensOut()).append(',')
              .append(s.getSource().name().toLowerCase()).append(',')
              .append(s.isSelfAssessed() ? "yes" : "no").append('\n');
        }
        return sb.toString();
    }

    /** Plain maps and lists, so Jackson writes them without any annotations and the shape is obvious in the file. */
    @Transactional(readOnly = true)
    public Map<String, Object> everything(Person person) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", Instant.now().toString());
        out.put("format", "sharpen-export/1");

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("email", person.getEmail());
        profile.put("displayName", person.getDisplayName());
        profile.put("firstName", person.getFirstName());
        profile.put("middleName", person.getMiddleName());
        profile.put("lastName", person.getLastName());
        profile.put("handle", person.getHandle());
        profile.put("accountType", person.getAccountType().name());
        profile.put("headline", person.getHeadline());
        profile.put("jobTitle", person.getJobTitle());
        profile.put("industry", person.getIndustry());
        profile.put("yearsExperience", person.getYearsExperience());
        profile.put("location", person.getLocation());
        profile.put("primaryTools", person.getPrimaryTools());
        profile.put("publicProfile", person.isPublicProfile());
        profile.put("createdAt", person.getCreatedAt() == null ? null : person.getCreatedAt().toString());
        out.put("profile", profile);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (UsageSession s : sessions.findByPersonIdOrderByOccurredOnAscIdAsc(person.getId())) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("date", s.getOccurredOn().toString());
            r.put("context", s.getContext().name());
            r.put("tool", s.getTool());
            r.put("task", s.getTaskCategory().name());
            r.put("minutes", s.getDurationMinutes());
            r.put("prompts", s.getPromptCount());
            r.put("humanPct", s.getHumanContributionPct());
            r.put("verified", s.isVerifiedOutput());
            r.put("learned", s.isLearnedSomething());
            r.put("outcome", s.getOutcome());
            r.put("notes", s.getNotes());
            r.put("externalId", s.getExternalId());
            r.put("tokensIn", s.getTokensIn());
            r.put("tokensOut", s.getTokensOut());
            r.put("source", s.getSource().name());
            r.put("selfAssessed", s.isSelfAssessed());
            r.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
            rows.add(r);
        }
        out.put("sessions", rows);

        List<Map<String, Object>> reps = new ArrayList<>();
        for (MonthlyReport m : reports.findByPersonIdOrderByYearMonthDesc(person.getId())) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("yearMonth", m.getYearMonth());
            r.put("aiScore", m.getAiScore());
            r.put("independence", m.getIndependence());
            r.put("effectiveness", m.getEffectiveness());
            r.put("verification", m.getVerification());
            r.put("growth", m.getGrowth());
            r.put("breadth", m.getBreadth());
            r.put("sessionCount", m.getSessionCount());
            r.put("totalMinutes", m.getTotalMinutes());
            r.put("generatedAt", m.getGeneratedAt() == null ? null : m.getGeneratedAt().toString());
            r.put("report", reportService.read(m));   // the full report as it was generated, not just the totals
            reps.add(r);
        }
        out.put("reports", reps);
        return out;
    }

    /** RFC 4180: quote when the value has a comma, quote or line break; double the quotes inside. */
    static String csv(String v) {
        if (v == null || v.isEmpty()) return "";
        boolean needs = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        return needs ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }
}
