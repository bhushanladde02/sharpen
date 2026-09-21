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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
    private final BuildProperties build;   // null when run from an IDE without the Maven build-info step

    public SessionApiController(PersonService people, ImportService imports, StatsService stats,
                                ObjectProvider<BuildProperties> build) {
        this.people = people;
        this.imports = imports;
        this.stats = stats;
        this.build = build.getIfAvailable();
    }

    /**
     * Liveness for the deploy script and uptime checkers, plus which build is answering — the deploy log says
     * what was rolled out, this says what is actually running. Open to everyone; contains nothing private.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("status", "ok");
        if (build != null) {
            out.put("version", build.getVersion());
            if (build.getTime() != null) out.put("built", build.getTime().toString());
        }
        return out;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Person p = people.requireCurrent();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("handle", p.getHandle()); out.put("displayName", p.getDisplayName()); out.put("accountType", p.getAccountType().name());
        if (!p.isCompany()) { out.put("firstName", p.getFirstName()); out.put("middleName", p.getMiddleName()); out.put("lastName", p.getLastName()); }
        return out;
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
