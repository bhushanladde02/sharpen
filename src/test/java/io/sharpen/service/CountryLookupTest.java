package io.sharpen.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The country list: parsing, lookup, refusing bad lists, and the monthly download. No Spring context. */
class CountryLookupTest {

    private static final String LIST = """
            1.0.0.0,1.0.0.255,AU
            8.8.8.0,8.8.8.255,US
            49.36.0.0,49.36.255.255,IN
            81.2.69.0,81.2.69.255,GB
            "2001:db8::","2001:db8:ffff:ffff:ffff:ffff:ffff:ffff","DE"
            2a00:1450::,2a00:1450:ffff:ffff:ffff:ffff:ffff:ffff,IE
            """;

    @TempDir Path dir;

    private Path write(String name, String content, boolean gzip) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, gzip ? gz(content) : content.getBytes(StandardCharsets.US_ASCII));
        return p;
    }

    private static byte[] gz(String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(out)) { z.write(content.getBytes(StandardCharsets.US_ASCII)); }
        return out.toByteArray();
    }

    @Test
    void looksUpIpv4Ipv6AndMappedAddressesFromAGzippedList() throws IOException {
        CountryLookup lookup = new CountryLookup(write("list.csv.gz", LIST, true).toString());
        assertThat(lookup.status().loaded()).isTrue();
        assertThat(lookup.status().ranges()).isEqualTo(6);
        assertThat(lookup.country("81.2.69.160")).isEqualTo("GB");
        assertThat(lookup.country("49.36.255.255")).isEqualTo("IN");      // last address of a range
        assertThat(lookup.country("8.8.8.0")).isEqualTo("US");            // first address of a range
        assertThat(lookup.country("2001:db8::1")).isEqualTo("DE");        // quoted fields are fine
        assertThat(lookup.country("[2a00:1450:4001::200e]")).isEqualTo("IE");
        assertThat(lookup.country("fe80::1%en0")).isEqualTo("ZZ");        // zone id stripped, then not in the list
        assertThat(lookup.country("::ffff:81.2.69.1")).isEqualTo("GB");   // IPv4-mapped IPv6 is the IPv4 address
        assertThat(lookup.country("0:0:0:0:0:0:0:1")).isEqualTo("ZZ");    // Tomcat's localhost
    }

    @Test
    void anythingElseIsUnknownAndNeverResolved() throws IOException {
        CountryLookup lookup = new CountryLookup(write("list.csv", LIST, false).toString());
        assertThat(lookup.country("81.2.70.1")).isEqualTo("ZZ");          // between ranges
        assertThat(lookup.country("255.255.255.255")).isEqualTo("ZZ");    // after the last range
        assertThat(lookup.country("0.0.0.1")).isEqualTo("ZZ");            // before the first
        assertThat(lookup.country("300.1.1.1")).isEqualTo("ZZ");
        assertThat(lookup.country("example.com")).isEqualTo("ZZ");        // a host name is not looked up in DNS
        assertThat(lookup.country("cafe")).isEqualTo("ZZ");               // hex-only, still not a literal
        assertThat(lookup.country("")).isEqualTo("ZZ");
        assertThat(lookup.country(null)).isEqualTo("ZZ");
    }

    @Test
    void withoutAListEveryAddressIsUnknown() {
        CountryLookup lookup = new CountryLookup(dir.resolve("missing.csv.gz").toString());
        assertThat(lookup.status().loaded()).isFalse();
        assertThat(lookup.country("81.2.69.160")).isEqualTo("ZZ");
    }

    @Test
    void unsortedListsAreSortedAndBadListsRefused() throws IOException {
        CountryLookup lookup = new CountryLookup(write("u.csv", "81.2.69.0,81.2.69.255,GB\n1.0.0.0,1.0.0.255,AU\n", false).toString());
        assertThat(lookup.country("1.0.0.7")).isEqualTo("AU");
        assertThat(lookup.country("81.2.69.7")).isEqualTo("GB");

        Path html = write("error.html", "<html><body>Not found</body></html>\n", false);
        assertThatThrownBy(() -> lookup.load(html, 1)).hasMessageContaining("not a usable country list");
        Path small = write("small.csv", LIST, false);
        assertThatThrownBy(() -> lookup.load(small, 100)).hasMessageContaining("6 ranges");
        assertThat(lookup.country("81.2.69.7")).isEqualTo("GB");          // the good list stayed in place
    }

    /** A stand-in for download.db-ip.com: serves the given months, 404 for the rest, and counts requests. */
    private record Stub(HttpServer server, List<String> requests) implements AutoCloseable {
        static Stub serving(String month, byte[] body) throws IOException {
            List<String> requests = new ArrayList<>();
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/free/", ex -> {
                requests.add(ex.getRequestURI().getPath());
                boolean hit = ex.getRequestURI().getPath().equals("/free/dbip-country-lite-" + month + ".csv.gz");
                ex.sendResponseHeaders(hit ? 200 : 404, hit ? body.length : -1);
                if (hit) ex.getResponseBody().write(body);
                ex.close();
            });
            s.start();
            return new Stub(s, requests);
        }
        String template() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/free/dbip-country-lite-{month}.csv.gz"; }
        @Override public void close() { server.stop(0); }
    }

    private static final Clock EARLY_OCTOBER = Clock.fixed(Instant.parse("2026-10-01T03:41:00Z"), ZoneOffset.UTC);

    @Test
    void downloadsLastMonthsEditionWhenThisMonthsIsNotOutYet() throws IOException {
        Path target = dir.resolve("geo/dbip-country-lite.csv.gz");
        CountryLookup lookup = new CountryLookup(target.toString());
        try (Stub stub = Stub.serving("2026-09", gz(LIST))) {
            CountryListUpdater updater = new CountryListUpdater(lookup, true, stub.template(), 1, EARLY_OCTOBER);
            assertThat(updater.refreshIfStale()).isTrue();
            assertThat(stub.requests()).containsExactly("/free/dbip-country-lite-2026-10.csv.gz", "/free/dbip-country-lite-2026-09.csv.gz");
            assertThat(lookup.country("49.36.1.1")).isEqualTo("IN");
            assertThat(Files.isRegularFile(target)).isTrue();
            assertThat(Files.exists(target.resolveSibling("dbip-country-lite.csv.gz.part"))).isFalse();

            // Fresh on disk: the nightly check does not call out again
            Files.setLastModifiedTime(target, FileTime.from(Instant.parse("2026-09-30T00:00:00Z")));
            assertThat(updater.refreshIfStale()).isFalse();
            assertThat(stub.requests()).hasSize(2);

            // A month later it is stale and is fetched again; a fresh process loads it from disk without asking
            Files.setLastModifiedTime(target, FileTime.from(Instant.parse("2026-08-25T00:00:00Z")));
            assertThat(updater.refreshIfStale()).isTrue();
            assertThat(new CountryLookup(target.toString()).country("81.2.69.9")).isEqualTo("GB");
        }
    }

    @Test
    void anUnusableDownloadKeepsTheCurrentList() throws IOException {
        Path target = write("dbip-country-lite.csv.gz", LIST, true);
        Files.setLastModifiedTime(target, FileTime.from(Instant.parse("2026-08-01T00:00:00Z")));   // stale
        CountryLookup lookup = new CountryLookup(target.toString());
        try (Stub stub = Stub.serving("2026-10", "<html>maintenance</html>".getBytes(StandardCharsets.US_ASCII))) {
            CountryListUpdater updater = new CountryListUpdater(lookup, true, stub.template(), 1, EARLY_OCTOBER);
            assertThat(updater.refreshIfStale()).isFalse();
            assertThat(lookup.country("81.2.69.160")).isEqualTo("GB");
            assertThat(new CountryLookup(target.toString()).country("81.2.69.160")).isEqualTo("GB");   // file untouched
        }
    }

    @Test
    void switchedOffItNeverCallsOut() throws IOException {
        CountryLookup lookup = new CountryLookup(dir.resolve("none.csv.gz").toString());
        try (Stub stub = Stub.serving("2026-10", gz(LIST))) {
            CountryListUpdater updater = new CountryListUpdater(lookup, false, stub.template(), 1, EARLY_OCTOBER);
            updater.atStartup();
            updater.nightly();
            assertThat(stub.requests()).isEmpty();
        }
    }
}
