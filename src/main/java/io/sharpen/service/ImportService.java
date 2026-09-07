package io.sharpen.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sharpen.domain.Enums.SessionSource;
import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.Person;
import io.sharpen.service.SessionService.SessionInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Three ways usage arrives without typing it in:
 * <ul>
 *   <li>Sharpen CSV — the full record, exported from this app or filled in a spreadsheet.</li>
 *   <li>Provider usage export — the CSV OpenAI/Anthropic dashboards produce: dates, models, request and token counts.
 *       No self-assessment is possible from it, so rows land as "needs rating".</li>
 *   <li>Extension JSON — what the browser extension posts: time and prompt counts per site per day.</li>
 * </ul>
 */
@Service
@Transactional
public class ImportService {

    public record ImportResult(int created, int updated, int skipped, List<String> errors) {
        public int total() { return created + updated; }
    }

    /** One session as sent by the browser extension or an API client. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalSession(String externalId, String date, String context, String tool, String task,
                                  Integer minutes, Integer prompts, Integer humanPct, Boolean verified,
                                  Boolean learned, Integer outcome, String notes, Long tokensIn, Long tokensOut) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExternalBatch(List<ExternalSession> sessions) {}

    private final SessionService sessions;
    private final ObjectMapper json;

    public ImportService(SessionService sessions, ObjectMapper json) {
        this.sessions = sessions;
        this.json = json;
    }

    public ImportResult importExternal(Person person, List<ExternalSession> batch, SessionSource source, UsageContext defaultContext) {
        int created = 0, updated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            ExternalSession e = batch.get(i);
            try {
                LocalDate date = parseDate(e.date());
                if (date == null) { skipped++; errors.add("Row " + (i + 1) + ": missing or unreadable date"); continue; }
                SessionInput in = new SessionInput(date,
                        parseContext(e.context(), defaultContext), e.tool(), parseTask(e.task()),
                        e.minutes(), e.prompts(), e.humanPct(), e.verified(), e.learned(), e.outcome(),
                        e.notes(), e.externalId(), e.tokensIn(), e.tokensOut());
                if (sessions.upsertExternal(person, in, source).created()) created++; else updated++;
            } catch (RuntimeException ex) {
                skipped++;
                errors.add("Row " + (i + 1) + ": " + ex.getMessage());
            }
        }
        return new ImportResult(created, updated, skipped, errors);
    }

    public ImportResult importExtensionJson(Person person, String body, UsageContext defaultContext) {
        try {
            ExternalBatch batch = json.readValue(body, ExternalBatch.class);
            if (batch.sessions() == null) return new ImportResult(0, 0, 0, List.of("No \"sessions\" array in the file"));
            return importExternal(person, batch.sessions(), SessionSource.EXTENSION, defaultContext);
        } catch (IOException e) {
            return new ImportResult(0, 0, 0, List.of("Not valid JSON: " + e.getMessage()));
        }
    }

    /** Auto-detects the Sharpen layout (has a "human_pct" column) versus a provider usage export. */
    public ImportResult importCsv(Person person, String csv, UsageContext defaultContext) {
        List<Map<String, String>> rows = parseCsv(csv);
        if (rows.isEmpty()) return new ImportResult(0, 0, 0, List.of("The file has no data rows"));
        Set<String> cols = rows.get(0).keySet();
        return cols.contains("human_pct") ? importSharpenCsv(person, rows, defaultContext)
                : importProviderUsage(person, rows, defaultContext);
    }

    private ImportResult importSharpenCsv(Person person, List<Map<String, String>> rows, UsageContext defaultContext) {
        List<ExternalSession> batch = new ArrayList<>();
        for (Map<String, String> r : rows) {
            batch.add(new ExternalSession(
                    blankToNull(r.get("external_id")), r.get("date"), r.get("context"), r.get("tool"), r.get("task"),
                    toInt(r.get("minutes")), toInt(r.get("prompts")), toInt(r.get("human_pct")),
                    toBool(r.get("verified")), toBool(r.get("learned")), toInt(r.get("outcome")),
                    r.get("notes"), toLong(r.get("tokens_in")), toLong(r.get("tokens_out"))));
        }
        return importExternal(person, batch, SessionSource.API_IMPORT, defaultContext);
    }

    /**
     * Provider dashboards export one row per day per model. Minutes are unknown, so they are estimated at three
     * minutes per request — visible on the session as an estimate until the person rates it.
     */
    private ImportResult importProviderUsage(Person person, List<Map<String, String>> rows, UsageContext defaultContext) {
        List<ExternalSession> batch = new ArrayList<>();
        for (Map<String, String> r : rows) {
            String date = first(r, "date", "timestamp", "day", "start_time");
            String model = first(r, "model", "snapshot_id", "model_id");
            Integer requests = toInt(first(r, "n_requests", "requests", "num_requests", "request_count", "messages"));
            Long in = toLong(first(r, "n_context_tokens_total", "input_tokens", "prompt_tokens", "context_tokens"));
            Long out = toLong(first(r, "n_generated_tokens_total", "output_tokens", "completion_tokens", "generated_tokens"));
            if (date == null) continue;
            int reqs = requests == null ? 1 : requests;
            String tool = toolFromModel(model);
            String extId = "usage:" + date.substring(0, Math.min(10, date.length())) + ":" + (model == null ? tool : model);
            batch.add(new ExternalSession(extId, date, null, tool, "OTHER", reqs * 3, reqs,
                    null, null, null, null, "Imported from usage export" + (model == null ? "" : " (" + model + ")"), in, out));
        }
        if (batch.isEmpty()) return new ImportResult(0, 0, rows.size(), List.of(
                "No recognisable columns. Expected a date column plus model / requests / token counts, or the Sharpen layout."));
        return importExternal(person, batch, SessionSource.API_IMPORT, defaultContext);
    }

    static String toolFromModel(String model) {
        if (model == null) return "API";
        String m = model.toLowerCase(Locale.ROOT);
        if (m.contains("gpt") || m.startsWith("o1") || m.startsWith("o3") || m.contains("davinci")) return "OpenAI API";
        if (m.contains("claude")) return "Claude API";
        if (m.contains("gemini")) return "Gemini API";
        if (m.contains("llama") || m.contains("mistral") || m.contains("mixtral")) return "Open model";
        return "API";
    }

    // ---- parsing helpers --------------------------------------------------------------------------------

    static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        try { return LocalDate.parse(t.length() > 10 ? t.substring(0, 10) : t); } catch (DateTimeParseException ignored) {}
        try { return OffsetDateTime.parse(t).toLocalDate(); } catch (DateTimeParseException ignored) {}
        try {
            long epoch = Long.parseLong(t);
            if (epoch > 10_000_000_000L) epoch /= 1000;
            return java.time.Instant.ofEpochSecond(epoch).atOffset(java.time.ZoneOffset.UTC).toLocalDate();
        } catch (NumberFormatException ignored) {}
        return null;
    }

    static UsageContext parseContext(String s, UsageContext dflt) {
        if (s == null || s.isBlank()) return dflt;
        String t = s.trim().toUpperCase(Locale.ROOT);
        return t.startsWith("PERS") ? UsageContext.PERSONAL : t.startsWith("PRO") || t.startsWith("WORK") ? UsageContext.PROFESSIONAL : dflt;
    }

    static TaskCategory parseTask(String s) {
        if (s == null || s.isBlank()) return TaskCategory.OTHER;
        String t = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('&', '_');
        String prefix = t.substring(0, Math.min(3, t.length()));
        for (TaskCategory c : TaskCategory.values()) {
            if (c.name().equals(t) || c.label.equalsIgnoreCase(s.trim()) || (prefix.length() == 3 && c.name().startsWith(prefix))) return c;
        }
        if (t.startsWith("DATA")) return TaskCategory.ANALYSIS;
        if (t.startsWith("EMAIL")) return TaskCategory.ADMIN;
        return TaskCategory.OTHER;
    }

    static Integer toInt(String s) {
        if (s == null || s.isBlank()) return null;
        try { return (int) Math.round(Double.parseDouble(s.trim())); } catch (NumberFormatException e) { return null; }
    }

    static Long toLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Math.round(Double.parseDouble(s.trim())); } catch (NumberFormatException e) { return null; }
    }

    static Boolean toBool(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return t.equals("true") || t.equals("yes") || t.equals("y") || t.equals("1");
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static String first(Map<String, String> row, String... keys) {
        for (String k : keys) {
            String v = row.get(k);
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    /** RFC 4180-ish: quoted fields, doubled quotes, CRLF or LF. Header names are lower-cased and trimmed. */
    static List<Map<String, String>> parseCsv(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> field = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else quoted = false;
                } else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { field.add(cur.toString()); cur.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                field.add(cur.toString()); cur.setLength(0);
                records.add(field); field = new ArrayList<>();
            } else cur.append(c);
        }
        if (cur.length() > 0 || !field.isEmpty()) { field.add(cur.toString()); records.add(field); }

        List<Map<String, String>> rows = new ArrayList<>();
        if (records.isEmpty()) return rows;
        List<String> header = records.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT).replace("﻿", "")).toList();
        for (int r = 1; r < records.size(); r++) {
            List<String> rec = records.get(r);
            if (rec.size() == 1 && rec.get(0).isBlank()) continue;
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) row.put(header.get(c), c < rec.size() ? rec.get(c) : "");
            rows.add(row);
        }
        return rows;
    }
}
