package com.gathin.tvhome;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Age ratings for the launcher's filters and the title preview (BACKLOG.md, Real catalogue). The screens speak the
 * US scale the prototype uses (G … NC-17 for movies, TV-Y … TV-MA for shows), so a title's rating is TMDB's US
 * certification, tidied; a title without one has "" (not rated here). Sweden's own ratings (Btl, 7, 11, 15) are an
 * open question for the project lead. Pure Java, unit-tested (CertificationsTest).
 */
final class Certifications {
    private Certifications() {}

    static final String COUNTRY = "US";

    private static final List<String> MOVIE = Arrays.asList("G", "PG", "PG-13", "R", "NC-17");
    private static final List<String> TV = Arrays.asList("TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14", "TV-MA");

    /** Highest a rating can rank: not rated, or a rating the scale doesn't know. */
    static final int UNRATED = 5;

    /**
     * A certification as the screens show it: trimmed and upper case, "NR" for TMDB's "Not Rated" and "Unrated",
     * and "" for anything that isn't on the US scales (another country's rating in the wrong field).
     */
    static String tidy(String cert) {
        if (cert == null) return "";
        String c = cert.trim().toUpperCase(Locale.ROOT).replace(' ', '-');
        if (c.equals("NR") || c.equals("NOT-RATED") || c.equals("UNRATED")) return "NR";
        if (c.equals("TV-Y7-FV")) return "TV-Y7";
        return MOVIE.contains(c) || TV.contains(c) ? c : "";
    }

    /**
     * A movie's rating from its US release dates: the theatrical release's (type 3), else the first that has one.
     * {@code types} and {@code certs} are the US release dates in TMDB's order.
     */
    static String movie(int[] types, String[] certs) {
        if (types == null || certs == null) return "";
        String first = "";
        for (int i = 0; i < Math.min(types.length, certs.length); i++) {
            String c = tidy(certs[i]);
            if (c.isEmpty()) continue;
            if (types[i] == 3) return c;
            if (first.isEmpty()) first = c;
        }
        return first;
    }

    /**
     * How strict a rating is, so filters can say "up to PG-13": 0 for everyone (G, TV-Y, TV-Y7, TV-G), 1 for PG and
     * TV-PG, 2 for PG-13 and TV-14, 3 for R and TV-MA, 4 for NC-17, and {@link #UNRATED} for NR, "" and anything else.
     */
    static int rank(String rating) {
        if (rating == null) return UNRATED;
        switch (rating) {
            case "G":
            case "TV-Y":
            case "TV-Y7":
            case "TV-G":
                return 0;
            case "PG":
            case "TV-PG":
                return 1;
            case "PG-13":
            case "TV-14":
                return 2;
            case "R":
            case "TV-MA":
                return 3;
            case "NC-17":
                return 4;
            default:
                return UNRATED;
        }
    }
}
