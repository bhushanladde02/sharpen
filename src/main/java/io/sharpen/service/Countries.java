package io.sharpen.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Country names and continents by ISO 3166-1 alpha-2 code, from {@code geo/countries.csv} (see its header for
 * the source and licence). Covers every code DB-IP's country list uses; anything else is shown as its code.
 */
public final class Countries {

    /** DB-IP's code, and ours, for an address that could not be placed. */
    public static final String UNKNOWN = "ZZ";

    public static final Map<String, String> CONTINENTS;
    private static final Map<String, String[]> BY_CODE;

    static {
        Map<String, String> continents = new LinkedHashMap<>();
        continents.put("NA", "North America");
        continents.put("SA", "South America");
        continents.put("EU", "Europe");
        continents.put("AS", "Asia");
        continents.put("AF", "Africa");
        continents.put("OC", "Oceania");
        continents.put("AN", "Antarctica");
        CONTINENTS = Collections.unmodifiableMap(continents);

        Map<String, String[]> byCode = new LinkedHashMap<>();
        try (var in = Objects.requireNonNull(Countries.class.getResourceAsStream("/geo/countries.csv"), "geo/countries.csv");
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split(",", 3);
                byCode.put(f[0], new String[]{f[1], f[2]});
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        BY_CODE = Collections.unmodifiableMap(byCode);
    }

    private Countries() {}

    /** "India" for {@code IN}; "Unknown" for {@link #UNKNOWN}; the code itself if it is not in the list. */
    public static String name(String code) {
        if (code == null || UNKNOWN.equals(code)) return "Unknown";
        String[] e = BY_CODE.get(code);
        return e == null ? code : e[1];
    }

    /** Continent code ({@code AS}) for a country code, or null when unknown. */
    public static String continent(String code) {
        String[] e = code == null ? null : BY_CODE.get(code);
        return e == null ? null : e[0];
    }

    /** "Asia" for {@code AS}. */
    public static String continentName(String continent) {
        return continent == null ? "Unknown" : CONTINENTS.getOrDefault(continent, continent);
    }

    public static boolean known(String code) {
        return code != null && BY_CODE.containsKey(code);
    }
}
