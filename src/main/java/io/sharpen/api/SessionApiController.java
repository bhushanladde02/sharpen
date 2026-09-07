package io.sharpen.api;

import io.sharpen.domain.Enums.SessionSource;
import io.sharpen.domain.Enums.UsageContext;
import io.sharpen.domain.Person;
import io.sharpen.scoring.AiScore;
import io.sharpen.service.ImportService;
import io.sharpen.service.ImportService.ExternalBatch;
import io.sharpen.service.ImportService.ImportResult;
import io.sharpen.service.PersonService;
import io.sharpen.service.StatsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * JSON API used by the browser extension and by anyone scripting an import. Authentication is the
 * {@code X-Api-Key} header (see Settings). All endpoints are per-person; there is no cross-account access.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionApiController {

    private final PersonService people;
    private final ImportService imports;
    private final StatsService stats;

    public SessionApiController(PersonService people, ImportService imports, StatsService stats) {
        this.people = people;
        this.imports = imports;
        this.stats = stats;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Person p = people.requireCurrent();
        return Map.of("handle", p.getHandle(), "displayName", p.getDisplayName(), "accountType", p.getAccountType().name());
    }

    @GetMapping("/me/score")
    public AiScore score() {
        return stats.rollingScore(people.requireCurrent(), LocalDate.now());
    }

    /**
     * Upsert a batch of sessions. Each needs a stable {@code externalId} so re-syncs are idempotent.
     * {@code source} is "extension" (default) or "import"; {@code defaultContext} applies to rows without one.
     */
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.OK)
    public ImportResult ingest(@RequestBody ExternalBatch batch,
                               @RequestParam(defaultValue = "extension") String source,
                               @RequestParam(defaultValue = "PROFESSIONAL") UsageContext defaultContext) {
        Person p = people.requireCurrent();
        SessionSource src = "import".equalsIgnoreCase(source) ? SessionSource.API_IMPORT : SessionSource.EXTENSION;
        return imports.importExternal(p, batch.sessions() == null ? List.of() : batch.sessions(), src, defaultContext);
    }
}
