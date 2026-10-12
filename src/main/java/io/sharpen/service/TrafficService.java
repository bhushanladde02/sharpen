package io.sharpen.service;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.MonthlyReport;
import io.sharpen.domain.PageView;
import io.sharpen.domain.Person;
import io.sharpen.repo.FeedbackRepository;
import io.sharpen.repo.MonthlyReportRepository;
import io.sharpen.repo.PageViewRepository;
import io.sharpen.repo.PersonRepository;
import io.sharpen.repo.UsageSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

/**
 * First-party, cookieless traffic analytics.
 *
 * <p>Recording: {@link #record} is called by {@code PageViewInterceptor} for every HTML page served; views
 * are buffered in memory and written in one batch every few seconds so a page never waits on the analytics
 * insert. Bots are dropped by user-agent. Visitors are identified for one day only, by a salted hash of
 * (day, address, user agent); the salt is generated at start-up and never stored. The country is looked up from
 * the address in memory ({@link CountryLookup}) and only the two-letter code is kept.
 *
 * <p>Reporting: daily series and totals over a range, combined with product events from the other tables
 * (sign-ups, sessions logged, reports generated) so a period can be described in one table.
 */
@Service
public class TrafficService {

    private static final Logger log = LoggerFactory.getLogger(TrafficService.class);
    /**
     * User agents that are not a person: search and social crawlers ("bot", "spider", "preview"…), HTTP libraries
     * and CLI tools (curl, okhttp, axios, node-fetch, Scrapy…), synthetic browsers (headless, PhantomJS, Lighthouse),
     * uptime checkers and internet-wide port scanners (zgrab, masscan, Censys, Nmap). A visit is only counted
     * when none of these match, so the traffic record errs on the side of under-counting.
     */
    private static final Pattern BOT = Pattern.compile(
            "bot|crawl|spider|slurp|scrap|archiver|fetch|curl|wget|python|java/|go-http|httpclient|okhttp|axios|libwww|" +
            "postman|insomnia|headless|phantom|lighthouse|pingdom|uptime|monitor|preview|validator|" +
            "zgrab|masscan|censys|nmap|nuclei|nikto|sqlmap|" +
            "facebookexternalhit|whatsapp|telegram|discord|embedly|vkshare|skypeuri",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_BUFFER = 5_000;

    public record Day(LocalDate day, long views, long visitors, long signups, long sessions, long reports) {}
    public record Row(String key, long views, long visitors) {}
    /** A country or a continent: its visitors, views, and its share of the visitors whose country is known. */
    public record Place(String code, String name, long views, long visitors, double share) {
        /** "42%" or "<1%" — whole percentages read faster than decimals and add up near enough. */
        public String percent() {
            long p = Math.round(share * 100);
            return visitors > 0 && p == 0 ? "<1%" : p + "%";
        }
    }
    /**
     * Where visitors came from. {@code located} is the visitors with a known country (what the shares are of);
     * {@code unknown} could not be placed (private networks, addresses missing from the list, or no list loaded);
     * {@code notRecorded} came before countries were recorded.
     */
    public record Places(List<Place> countries, List<Place> continents, long located, long unknown, long notRecorded) {}
    public record Summary(LocalDate from, LocalDate to, long views, long visitors, long signups, long sessions,
                          long reports, long members, long publicProfiles, long feedback,
                          List<Day> days, List<Row> pages, List<Row> referrers, List<Row> languages, Places places) {}

    private final PageViewRepository views;
    private final PersonRepository people;
    private final UsageSessionRepository sessions;
    private final MonthlyReportRepository reports;
    private final FeedbackRepository feedback;
    private final CountryLookup countries;
    private final ConcurrentLinkedQueue<PageView> buffer = new ConcurrentLinkedQueue<>();
    private final byte[] salt = new byte[32];

    public TrafficService(PageViewRepository views, PersonRepository people, UsageSessionRepository sessions,
                          MonthlyReportRepository reports, FeedbackRepository feedback, CountryLookup countries) {
        this.views = views;
        this.people = people;
        this.sessions = sessions;
        this.reports = reports;
        this.feedback = feedback;
        this.countries = countries;
        new SecureRandom().nextBytes(salt);
    }

    // ---------------------------------------------------------------- recording

    /** Queue one view. Cheap and non-blocking; drops silently if the buffer is full or the agent is a bot. */
    public void record(String path, String referrerHeader, String address, String userAgent, String acceptLanguage, boolean signedIn) {
        if (userAgent == null || userAgent.isBlank() || BOT.matcher(userAgent).find()) return;
        if (buffer.size() >= MAX_BUFFER) return;
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        String referrer = referrerHost(referrerHeader);
        String lang = acceptLanguage == null ? null : acceptLanguage.split("[,;]")[0].trim();
        if (lang != null && (lang.isEmpty() || lang.length() > 16)) lang = null;
        if (path.startsWith("/check/r/")) path = "/check/r/…";   // one line for all habits-check results, not one per answer set
        buffer.add(new PageView(day, path.length() > 200 ? path.substring(0, 200) : path, referrer,
                visitorHash(day, address, userAgent), lang, signedIn, countries.country(address)));
    }

    /** Every 10 s, write whatever has been queued. */
    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    @Transactional
    public void flush() {
        if (buffer.isEmpty()) return;
        List<PageView> batch = new ArrayList<>();
        PageView v;
        while ((v = buffer.poll()) != null) batch.add(v);
        try {
            views.saveAll(batch);
        } catch (RuntimeException e) {
            log.warn("Could not store {} page views: {}", batch.size(), e.getMessage());
        }
    }

    /** Referring host only, and never our own host. Returns null for direct visits. */
    static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) return null;
        try {
            String host = java.net.URI.create(referrer.trim()).getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) host = host.substring(4);
            return host.length() > 190 ? host.substring(0, 190) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String visitorHash(LocalDate day, String address, String userAgent) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(day.toString().getBytes(StandardCharsets.UTF_8));
            md.update((address == null ? "" : address).getBytes(StandardCharsets.UTF_8));
            md.update(userAgent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- reporting

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) byDay.put(d, new long[5]);
        for (Object[] r : views.daily(from, to)) {
            long[] a = byDay.get((LocalDate) r[0]);
            if (a != null) { a[0] = ((Number) r[1]).longValue(); a[1] = ((Number) r[2]).longValue(); }
        }
        List<Person> everyone = people.findAll();
        for (Person p : everyone) bump(byDay, LocalDate.ofInstant(p.getCreatedAt(), ZoneOffset.UTC), 2);
        Instant fromI = from.atStartOfDay(ZoneOffset.UTC).toInstant(), toI = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        for (Object[] r : sessions.createdPerDay(fromI, toI)) {
            long[] a = byDay.get(LocalDate.ofInstant((Instant) r[0], ZoneOffset.UTC));
            if (a != null) a[3] += ((Number) r[1]).longValue();
        }
        for (MonthlyReport r : reports.findByGeneratedAtBetween(fromI, toI)) bump(byDay, LocalDate.ofInstant(r.getGeneratedAt(), ZoneOffset.UTC), 4);

        List<Day> days = new ArrayList<>();
        long views = 0, visitors = 0, signups = 0, sess = 0, reps = 0;
        for (Map.Entry<LocalDate, long[]> e : byDay.entrySet()) {
            long[] a = e.getValue();
            days.add(new Day(e.getKey(), a[0], a[1], a[2], a[3], a[4]));
            views += a[0]; visitors += a[1]; signups += a[2]; sess += a[3]; reps += a[4];
        }
        long members = everyone.stream().filter(p -> p.getAccountType() == AccountType.INDIVIDUAL).count();
        long pub = everyone.stream().filter(p -> p.getAccountType() == AccountType.INDIVIDUAL && p.isPublicProfile()).count();
        return new Summary(from, to, views, visitors, signups, sess, reps, members, pub, feedback.count(), days,
                rows(this.views.topPaths(from, to), 15), rows(this.views.topReferrers(from, to), 15),
                rows(this.views.topLanguages(from, to), 10), places(this.views.countries(from, to)));
    }

    /** Countries most visitors first, and the same visitors grouped by continent. */
    public static Places places(List<Object[]> raw) {
        long located = 0, unknown = 0, notRecorded = 0;
        List<long[]> counts = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (Object[] r : raw) {
            String code = (String) r[0];
            long views = ((Number) r[1]).longValue(), visitors = ((Number) r[2]).longValue();
            if (code == null) notRecorded += visitors;
            else if (!Countries.known(code)) unknown += visitors;
            else { located += visitors; codes.add(code); counts.add(new long[]{views, visitors}); }
        }
        List<Place> countryList = new ArrayList<>();
        Map<String, long[]> byContinent = new LinkedHashMap<>();
        for (int i = 0; i < codes.size(); i++) {
            long[] c = counts.get(i);
            countryList.add(new Place(codes.get(i), Countries.name(codes.get(i)), c[0], c[1], located == 0 ? 0 : c[1] / (double) located));
            long[] k = byContinent.computeIfAbsent(Countries.continent(codes.get(i)), x -> new long[2]);
            k[0] += c[0];
            k[1] += c[1];
        }
        countryList.sort(Comparator.comparingLong(Place::visitors).reversed().thenComparing(Comparator.comparingLong(Place::views).reversed())
                .thenComparing(Place::name));
        List<Place> continentList = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byContinent.entrySet()) {
            continentList.add(new Place(e.getKey(), Countries.continentName(e.getKey()), e.getValue()[0], e.getValue()[1],
                    located == 0 ? 0 : e.getValue()[1] / (double) located));
        }
        continentList.sort(Comparator.comparingLong(Place::visitors).reversed().thenComparing(Place::name));
        return new Places(countryList, continentList, located, unknown, notRecorded);
    }

    private static void bump(Map<LocalDate, long[]> byDay, LocalDate d, int idx) {
        long[] a = byDay.get(d);
        if (a != null) a[idx]++;
    }

    private static List<Row> rows(List<Object[]> raw, int limit) {
        List<Row> out = new ArrayList<>();
        for (Object[] r : raw) {
            if (out.size() == limit) break;
            String key = r[0] == null ? "(direct)" : r[0].toString();
            long views = ((Number) r[1]).longValue();
            long visitors = r.length > 2 ? ((Number) r[2]).longValue() : 0;
            out.add(new Row(key, views, visitors));
        }
        return out;
    }

    public LocalDate firstDay() {
        LocalDate d = views.firstDay();
        return d == null ? LocalDate.now(ZoneOffset.UTC) : d;
    }

    /** CSV of the daily series — the machine-readable half of the evidence export. */
    public String csv(Summary s) {
        StringBuilder sb = new StringBuilder("day,page_views,visitors,signups,sessions_logged,reports_generated\n");
        for (Day d : s.days()) sb.append(d.day()).append(',').append(d.views()).append(',').append(d.visitors()).append(',')
                .append(d.signups()).append(',').append(d.sessions()).append(',').append(d.reports()).append('\n');
        return sb.toString();
    }
}
