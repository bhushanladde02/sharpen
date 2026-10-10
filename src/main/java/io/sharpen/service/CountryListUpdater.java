package io.sharpen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps the country list fresh: DB-IP publishes "IP to Country Lite" at the start of every month as
 * {@code dbip-country-lite-YYYY-MM.csv.gz}. Once at start-up and every night, if the file on disk is missing or
 * older than {@link #MAX_AGE}, this downloads the current month's edition (or last month's, early in a month),
 * checks that it parses, and swaps it in. DB-IP sees one request a month from the server and nothing about
 * visitors. Off unless {@code sharpen.geo.download} is true (production); tests and development never call out.
 */
@Service
public class CountryListUpdater {

    private static final Logger log = LoggerFactory.getLogger(CountryListUpdater.class);
    static final Duration MAX_AGE = Duration.ofDays(32);
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    private final CountryLookup lookup;
    private final boolean enabled;
    private final String urlTemplate;
    private final int minRanges;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();

    @Autowired
    public CountryListUpdater(CountryLookup lookup,
                              @Value("${sharpen.geo.download:false}") boolean enabled,
                              @Value("${sharpen.geo.source:https://download.db-ip.com/free/dbip-country-lite-{month}.csv.gz}") String urlTemplate,
                              @Value("${sharpen.geo.min-ranges:100000}") int minRanges) {
        this(lookup, enabled, urlTemplate, minRanges, Clock.systemUTC());
    }

    CountryListUpdater(CountryLookup lookup, boolean enabled, String urlTemplate, int minRanges, Clock clock) {
        this.lookup = lookup;
        this.enabled = enabled;
        this.urlTemplate = urlTemplate;
        this.minRanges = minRanges;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void atStartup() {
        if (enabled) CompletableFuture.runAsync(this::refreshIfStale);   // never hold up start-up on a download
    }

    /** Nightly at 03:41 UTC. */
    @Scheduled(cron = "0 41 3 * * *", zone = "UTC")
    public void nightly() {
        if (enabled) refreshIfStale();
    }

    /** True when a new list was downloaded and loaded. */
    public synchronized boolean refreshIfStale() {
        Path target = lookup.file();
        try {
            if (Files.isRegularFile(target) && lookup.status().loaded()
                    && Files.getLastModifiedTime(target).toInstant().isAfter(Instant.now(clock).minus(MAX_AGE))) {
                return false;
            }
            Files.createDirectories(target.toAbsolutePath().getParent());
        } catch (IOException e) {
            log.warn("Country list: cannot use {}: {}", target, e.getMessage());
            return false;
        }
        YearMonth now = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        for (YearMonth month : List.of(now, now.minusMonths(1))) {
            String url = urlTemplate.replace("{month}", month.toString());
            Path part = target.resolveSibling(target.getFileName() + ".part");
            try {
                if (!download(url, part)) continue;
                int ranges = lookup.install(part, target, minRanges);   // throws, keeping the current list, if unusable
                log.info("Country list updated from {} ({} ranges)", url, ranges);
                return true;
            } catch (IOException | RuntimeException e) {
                log.warn("Country list: {} was not usable: {}", url, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                try { Files.deleteIfExists(part); } catch (IOException ignored) { /* best effort */ }
            }
        }
        log.warn("Country list: no edition could be downloaded; keeping {}", lookup.status().loaded() ? "the current one" : "countries unknown");
        return false;
    }

    /** Fetch {@code url} into {@code part}; false on 404 (that month's edition is not out yet). */
    private boolean download(String url, Path part) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3))
                .header("User-Agent", "Sharpen (+https://github.com/bhushanladde02/sharpen)").GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = res.body()) {
            if (res.statusCode() == 404) return false;
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
            try (OutputStream out = Files.newOutputStream(part)) {
                byte[] buf = new byte[1 << 16];
                long total = 0;
                int n;
                while ((n = body.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) throw new IOException("larger than " + MAX_BYTES + " bytes");
                    out.write(buf, 0, n);
                }
            }
        }
        return true;
    }
}
