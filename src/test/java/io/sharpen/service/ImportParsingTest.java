package io.sharpen.service;

import io.sharpen.domain.Enums.TaskCategory;
import io.sharpen.domain.Enums.UsageContext;

import org.junit.jupiter.api.Test;


import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Parser behaviour, no Spring context. */
class ImportParsingTest {

    @Test
    void parsesQuotedCsvWithCrlf() {
        String csv = "Date,Tool,Notes\r\n2026-09-01,Claude,\"Hello, \"\"world\"\"\"\r\n2026-09-02,ChatGPT,plain\r\n";
        List<Map<String, String>> rows = parse(csv);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("date", "2026-09-01").containsEntry("notes", "Hello, \"world\"");
        assertThat(rows.get(1)).containsEntry("tool", "ChatGPT");
    }

    @Test
    void parsesDatesInSeveralShapes() {
        assertThat(ImportService.parseDate("2026-09-01")).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(ImportService.parseDate("2026-09-01T10:15:00Z")).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(ImportService.parseDate("1756700000")).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(ImportService.parseDate("not a date")).isNull();
    }

    @Test
    void mapsContextAndTaskLoosely() {
        assertThat(ImportService.parseContext("work", UsageContext.PERSONAL)).isEqualTo(UsageContext.PROFESSIONAL);
        assertThat(ImportService.parseContext("Personal", UsageContext.PROFESSIONAL)).isEqualTo(UsageContext.PERSONAL);
        assertThat(ImportService.parseContext("", UsageContext.PERSONAL)).isEqualTo(UsageContext.PERSONAL);
        assertThat(ImportService.parseTask("Data & analysis")).isEqualTo(TaskCategory.ANALYSIS);
        assertThat(ImportService.parseTask("code")).isEqualTo(TaskCategory.CODING);
        assertThat(ImportService.parseTask("email")).isEqualTo(TaskCategory.ADMIN);
        assertThat(ImportService.parseTask("???")).isEqualTo(TaskCategory.OTHER);
    }

    @Test
    void derivesToolFromModelName() {
        assertThat(ImportService.toolFromModel("gpt-4o-mini")).isEqualTo("OpenAI API");
        assertThat(ImportService.toolFromModel("claude-sonnet-4")).isEqualTo("Claude API");
        assertThat(ImportService.toolFromModel(null)).isEqualTo("API");
    }

    private static List<Map<String, String>> parse(String csv) {
        return ImportService.parseCsv(csv);
    }
}
