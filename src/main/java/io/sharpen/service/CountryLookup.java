package io.sharpen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * The country a network address belongs to, looked up in memory in DB-IP's free "IP to Country Lite" list
 * (CC BY 4.0, https://db-ip.com). Nothing leaves the server: the list is a file on disk, kept fresh by
 * {@link CountryListUpdater}, and the address itself is never stored — only the two-letter answer.
 *
 * <p>The list is DB-IP's CSV ({@code start,end,CC} per line, IPv4 and IPv6 mixed, optionally gzipped): about
 * 700,000 ranges, held as sorted arrays (about 25 MB of memory, loaded in about a second) and searched by binary search in well under a
 * microsecond. Until a list is loaded every address is {@link Countries#UNKNOWN}.
 */
@Service
public class CountryLookup {

    private static final Logger log = LoggerFactory.getLogger(CountryLookup.class);
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern CODE = Pattern.compile("[A-Z]{2}");

    /** What the traffic page says about the list. */
    public record Status(boolean loaded, String file, LocalDate fileDate, int ranges) {}

    /** Ranges of one address family, sorted by start; v4 uses only {@code lo}. */
    record Table(long[] startHi, long[] startLo, long[] endHi, long[] endLo, short[] country, String[] codes) {
        int size() { return country.length; }
    }

    private record Loaded(Table v4, Table v6, Status status) {}

    private static final Loaded EMPTY = new Loaded(null, null, new Status(false, null, null, 0));

    private final Path file;
    private volatile Loaded loaded = EMPTY;

    public CountryLookup(@Value("${sharpen.geo.file:data/geo/dbip-country-lite.csv.gz}") String file) {
        this.file = Path.of(file);
        if (Files.isRegularFile(this.file)) {
            try {
                load(this.file, 1);
            } catch (IOException | RuntimeException e) {
                log.warn("Country list {} could not be read ({}); countries stay unknown until it is replaced", file, e.getMessage());
            }
        } else {
            log.info("No country list at {} yet; countries are recorded as unknown until one is downloaded", this.file.toAbsolutePath());
        }
    }

    public Path file() { return file; }

    public Status status() { return loaded.status(); }

    /** Two-letter country code for an address, or {@link Countries#UNKNOWN}. Never throws, never resolves names. */
    public String country(String address) {
        Loaded l = loaded;
        if (address == null || l.v4() == null) return Countries.UNKNOWN;
        long[] k = key(address.trim());
        if (k == null) return Countries.UNKNOWN;
        Table t = k[0] == 4 ? l.v4() : l.v6();
        int i = find(t, k[1], k[2]);
        return i < 0 ? Countries.UNKNOWN : t.codes()[t.country()[i]];
    }

    /**
     * Read a list and swap it in. Refuses (and keeps the current one) when it holds fewer than {@code minRanges}
     * usable ranges or more than 1 % unreadable lines, so a truncated or wrong download never replaces a good list.
     */
    public synchronized int load(Path path, int minRanges) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            Loaded l = parse(in, path.getFileName().toString(), Files.getLastModifiedTime(path).toInstant(), minRanges);
            loaded = l;
            log.info("Country list loaded: {} ({} ranges)", path, l.status().ranges());
            return l.status().ranges();
        }
    }

    /** Check a downloaded list, move it over {@code target} and swap it in; the current list stays if it is unusable. */
    public synchronized int install(Path candidate, Path target, int minRanges) throws IOException {
        Loaded l;
        try (InputStream in = Files.newInputStream(candidate)) {
            l = parse(in, target.getFileName().toString(), Instant.now(), minRanges);
        }
        Files.move(candidate, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        loaded = l;
        return l.status().ranges();
    }

    static Loaded parse(InputStream raw, String name, Instant date, int minRanges) throws IOException {
        InputStream in = new BufferedInputStream(raw);
        in.mark(2);
        int b1 = in.read(), b2 = in.read();
        in.reset();
        if (b1 == 0x1f && b2 == 0x8b) in = new GZIPInputStream(in, 1 << 16);

        Builder v4 = new Builder(), v6 = new Builder();
        Map<String, Short> codeIndex = new HashMap<>();
        List<String> codes = new ArrayList<>();
        int bad = 0, lines = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII), 1 << 16)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                lines++;
                String[] f = line.replace("\"", "").split(",");
                if (f.length < 3) { bad++; continue; }
                long[] s = key(f[0].trim()), e = key(f[1].trim());
                String cc = f[f.length - 1].trim();
                if (s == null || e == null || s[0] != e[0] || !CODE.matcher(cc).matches()) { bad++; continue; }
                short ci = codeIndex.computeIfAbsent(cc, c -> { codes.add(c); return (short) (codes.size() - 1); });
                (s[0] == 4 ? v4 : v6).add(s[1], s[2], e[1], e[2], ci);
            }
        }
        int ranges = v4.n + v6.n;
        if (ranges < Math.max(1, minRanges) || bad > lines / 100) {
            throw new IOException("not a usable country list: " + ranges + " ranges, " + bad + " unreadable lines of " + lines);
        }
        String[] codeArray = codes.toArray(String[]::new);
        return new Loaded(v4.build(codeArray), v6.build(codeArray), new Status(true, name, LocalDate.ofInstant(date, ZoneOffset.UTC), ranges));
    }

    /** {family, hi, lo} for an IPv4 or IPv6 literal (IPv4-mapped IPv6 counts as IPv4), or null if it is neither. */
    static long[] key(String a) {
        if (a == null || a.isEmpty()) return null;
        if (IPV4.matcher(a).matches()) {
            long v = 0;
            for (String part : a.split("\\.")) {
                int o = Integer.parseInt(part);
                if (o > 255) return null;
                v = (v << 8) | o;
            }
            return new long[]{4, 0, v};
        }
        // Only something with a colon is handed to InetAddress: for those it parses the literal and never asks DNS.
        if (a.startsWith("[") && a.endsWith("]")) a = a.substring(1, a.length() - 1);
        int zone = a.indexOf('%');
        if (zone >= 0) a = a.substring(0, zone);
        if (a.indexOf(':') < 0 || !a.chars().allMatch(c -> c == ':' || c == '.' || Character.digit(c, 16) >= 0)) return null;
        try {
            byte[] b = InetAddress.getByName(a).getAddress();
            if (b.length == 4) return key(((Inet4Address) InetAddress.getByAddress(b)).getHostAddress());
            long hi = 0, lo = 0;
            for (int i = 0; i < 8; i++) hi = (hi << 8) | (b[i] & 0xff);
            for (int i = 8; i < 16; i++) lo = (lo << 8) | (b[i] & 0xff);
            return new long[]{6, hi, lo};
        } catch (UnknownHostException | ClassCastException e) {
            return null;
        }
    }

    /** Index of the range holding (hi, lo), or -1. */
    static int find(Table t, long hi, long lo) {
        int lowI = 0, highI = t.size() - 1, best = -1;
        while (lowI <= highI) {                              // last range whose start <= key
            int mid = (lowI + highI) >>> 1;
            if (compare(t.startHi()[mid], t.startLo()[mid], hi, lo) <= 0) { best = mid; lowI = mid + 1; }
            else highI = mid - 1;
        }
        if (best < 0 || compare(hi, lo, t.endHi()[best], t.endLo()[best]) > 0) return -1;
        return best;
    }

    private static int compare(long aHi, long aLo, long bHi, long bLo) {
        int c = Long.compareUnsigned(aHi, bHi);
        return c != 0 ? c : Long.compareUnsigned(aLo, bLo);
    }

    private static final class Builder {
        long[] sHi = new long[1024], sLo = new long[1024], eHi = new long[1024], eLo = new long[1024];
        short[] cc = new short[1024];
        int n;

        void add(long startHi, long startLo, long endHi, long endLo, short c) {
            if (n == cc.length) {
                int m = n * 2;
                sHi = Arrays.copyOf(sHi, m); sLo = Arrays.copyOf(sLo, m); eHi = Arrays.copyOf(eHi, m); eLo = Arrays.copyOf(eLo, m);
                cc = Arrays.copyOf(cc, m);
            }
            sHi[n] = startHi; sLo[n] = startLo; eHi[n] = endHi; eLo[n] = endLo; cc[n] = c;
            n++;
        }

        Table build(String[] codes) {
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            boolean sorted = true;
            for (int i = 1; i < n && sorted; i++) sorted = compare(sHi[i - 1], sLo[i - 1], sHi[i], sLo[i]) <= 0;
            if (!sorted) Arrays.sort(order, (a, b) -> compare(sHi[a], sLo[a], sHi[b], sLo[b]));
            Table t = new Table(new long[n], new long[n], new long[n], new long[n], new short[n], codes);
            for (int i = 0; i < n; i++) {
                int j = order[i];
                t.startHi()[i] = sHi[j]; t.startLo()[i] = sLo[j]; t.endHi()[i] = eHi[j]; t.endLo()[i] = eLo[j]; t.country()[i] = cc[j];
            }
            return t;
        }
    }
}
